// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.collect.nestedset;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingOutputStream;
import com.google.devtools.build.lib.skyframe.serialization.DeserializationContext;
import com.google.devtools.build.lib.skyframe.serialization.EnumCodec;
import com.google.devtools.build.lib.skyframe.serialization.ObjectCodec;
import com.google.devtools.build.lib.skyframe.serialization.SerializationConstants;
import com.google.devtools.build.lib.skyframe.serialization.SerializationContext;
import com.google.devtools.build.lib.skyframe.serialization.SerializationException;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Codec for {@link NestedSet}.
 *
 * <p>Nested sets are serialized by sorting the sub-graph in topological order, then writing the
 * nodes in that order. As a node is written we remember its digest. When serializing a node higher
 * in the graph, we replace any edge to another nested set with its digest.
 */
public class NestedSetCodec<T> implements ObjectCodec<NestedSet<T>> {

  private static final EnumCodec<Order> orderCodec = new EnumCodec<>(Order.class);

  private static final ConcurrentMap<ByteString, Object> digestToChild =
      CacheBuilder.newBuilder()
          .concurrencyLevel(SerializationConstants.DESERIALIZATION_POOL_SIZE)
          .weakValues()
          .<ByteString, Object>build()
          .asMap();

  public NestedSetCodec() {}

  @SuppressWarnings("unchecked")
  @Override
  public Class<NestedSet<T>> getEncodedClass() {
    // Compiler doesn't like cast from Class<NestedSet> -> Class<NestedSet<T>>, but it
    // does allow what we see below. Type is lost at runtime anyway, so while gross this works.
    return (Class<NestedSet<T>>) ((Class<?>) NestedSet.class);
  }

  @Override
  public void serialize(SerializationContext context, NestedSet<T> obj, CodedOutputStream codedOut)
      throws SerializationException, IOException {
    if (!SerializationConstants.shouldSerializeNestedSet) {
      // Don't perform NestedSet serialization in testing
      return;
    }

    // Topo sort the nested set to ensure digests are available for children at time of writing
    Collection<Object> topoSortedChildren = getTopologicallySortedChildren(obj);
    Map<Object, byte[]> childToDigest = new IdentityHashMap<>();
    codedOut.writeInt32NoTag(topoSortedChildren.size());
    orderCodec.serialize(context, obj.getOrder(), codedOut);
    for (Object children : topoSortedChildren) {
      serializeOneNestedSet(context, children, codedOut, childToDigest);
    }
  }

  @Override
  public NestedSet<T> deserialize(DeserializationContext context, CodedInputStream codedIn)
      throws SerializationException, IOException {
    if (!SerializationConstants.shouldSerializeNestedSet) {
      // Don't perform NestedSet deserialization in testing
      return NestedSetBuilder.emptySet(Order.STABLE_ORDER);
    }

    int nestedSetCount = codedIn.readInt32();
    Preconditions.checkState(
        nestedSetCount >= 1,
        "Should have at least serialized one nested set, got: %s",
        nestedSetCount);
    Order order = orderCodec.deserialize(context, codedIn);
    Object children = null;
    for (int i = 0; i < nestedSetCount; ++i) {
      // Update var, the last one in the list is the top level nested set
      children = deserializeOneNestedSet(context, codedIn);
    }
    return createNestedSet(order, children);
  }

  private void serializeOneNestedSet(
      SerializationContext context,
      Object children,
      CodedOutputStream codedOut,
      Map<Object, byte[]> childToDigest)
      throws IOException, SerializationException {
    // Serialize nested set into an inner byte array so we can take its digest
    ByteArrayOutputStream childOutputStream = new ByteArrayOutputStream();
    HashingOutputStream hashingOutputStream =
        new HashingOutputStream(Hashing.md5(), childOutputStream);
    CodedOutputStream childCodedOut = CodedOutputStream.newInstance(hashingOutputStream);
    if (children instanceof Object[]) {
      serializeMultiItemChildArray(context, (Object[]) children, childToDigest, childCodedOut);
    } else if (children != NestedSet.EMPTY_CHILDREN) {
      serializeSingleItemChildArray(context, children, childCodedOut);
    } else {
      // Empty set
      childCodedOut.writeInt32NoTag(0);
    }
    childCodedOut.flush();
    byte[] digest = hashingOutputStream.hash().asBytes();
    codedOut.writeByteArrayNoTag(digest);
    byte[] childBytes = childOutputStream.toByteArray();
    codedOut.writeByteArrayNoTag(childBytes);
    childToDigest.put(children, digest);
  }

  private void serializeMultiItemChildArray(
      SerializationContext context,
      Object[] children,
      Map<Object, byte[]> childToDigest,
      CodedOutputStream childCodedOut)
      throws IOException, SerializationException {
    childCodedOut.writeInt32NoTag(children.length);
    for (Object child : children) {
      if (child instanceof Object[]) {
        byte[] digest =
            Preconditions.checkNotNull(
                childToDigest.get(child),
                "Digest not available; Are nested sets serialized in the right order?");
        childCodedOut.writeBoolNoTag(true);
        childCodedOut.writeByteArrayNoTag(digest);
      } else {
        childCodedOut.writeBoolNoTag(false);
        context.serialize(cast(child), childCodedOut);
      }
    }
  }

  private void serializeSingleItemChildArray(
      SerializationContext context, Object children, CodedOutputStream childCodedOut)
      throws IOException, SerializationException {
    childCodedOut.writeInt32NoTag(1);
    T singleChild = cast(children);
    context.serialize(singleChild, childCodedOut);
  }

  private Object deserializeOneNestedSet(DeserializationContext context, CodedInputStream codedIn)
      throws SerializationException, IOException {
    ByteString digest = codedIn.readBytes();
    CodedInputStream childCodedIn = codedIn.readBytes().newCodedInput();
    childCodedIn.enableAliasing(true); // Allow efficient views of byte slices when reading digests
    int childCount = childCodedIn.readInt32();
    final Object result;
    if (childCount > 1) {
      result = deserializeMultipleItemChildArray(context, childCodedIn, childCount);
    } else if (childCount == 1) {
      result = context.deserialize(childCodedIn);
    } else {
      result = NestedSet.EMPTY_CHILDREN;
    }
    // If this member has been deserialized already, use it, so that NestedSets that share members
    // will point at the same object in memory instead of duplicating them.  Unfortunately, we had
    // to do the work of deserializing the member that we will now throw away, in order to ensure
    // that the pointer in the codedIn buffer was incremented appropriately.
    Object oldValue =
        digestToChild.putIfAbsent(
            // Copy the ByteString to avoid keeping the full buffer from codedIn alive due to
            // aliasing.
            ByteString.copyFrom(digest.asReadOnlyByteBuffer()), result);
    return oldValue == null ? result : oldValue;
  }

  private Object deserializeMultipleItemChildArray(
      DeserializationContext context,
      CodedInputStream childCodedIn,
      int childCount)
      throws IOException, SerializationException {
    Object[] children = new Object[childCount];
    for (int i = 0; i < childCount; ++i) {
      boolean isTransitiveEntry = childCodedIn.readBool();
      if (isTransitiveEntry) {
        ByteString digest = childCodedIn.readBytes();
        children[i] =
            Preconditions.checkNotNull(digestToChild.get(digest), "Transitive nested set missing");
      } else {
        children[i] = context.deserialize(childCodedIn);
      }
    }
    return children;
  }

  @SuppressWarnings("unchecked")
  private T cast(Object object) {
    return (T) object;
  }

  private static Collection<Object> getTopologicallySortedChildren(
      NestedSet<?> nestedSet) {
    LinkedHashSet<Object> result = new LinkedHashSet<>();
    dfs(result, nestedSet.rawChildren());
    return result;
  }

  private static <T> NestedSet<T> createNestedSet(Order order, Object children) {
    if (children == NestedSet.EMPTY_CHILDREN) {
      return order.emptySet();
    }
    return new NestedSet<>(order, children);
  }

  private static void dfs(LinkedHashSet<Object> sets, Object children) {
    if (sets.contains(children)) {
      return;
    }
    if (children instanceof Object[]) {
      for (Object child : (Object[]) children) {
        if (child instanceof Object[]) {
          dfs(sets, child);
        }
      }
    }
    sets.add(children);
  }
}
