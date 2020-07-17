/* 
 * Copyright (C) 2018-2019 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 * 
 */

package com.intel.pmem.llpl;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements a read and write interface for accessing a previously allocated block of memory on a heap. Access through a 
 * {@code PersistentAccessor} is bounds-checked to be within the allocated block of memory.
 *  
 * 
 * @since 1.1
 */

public final class PersistentAccessor extends AnyAccessor {

    static final long METADATA_SIZE = 8;

    PersistentAccessor(PersistentHeap heap) {
        super(heap);
    }

    /**
    * Returns the heap associated with this accessor.
    * @return the {@code Heap} associated with this accessor 
    */
    public PersistentHeap heap() {
        return (PersistentHeap)super.heap();
    }
    
    /**
     * {@inheritDoc}
     * @param offset {@inheritDoc}
     * @param value {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void setByte(long offset, byte value) {
        super.durableSetByte(offset, value);
    }

    /**
     * {@inheritDoc}
     * @param offset {@inheritDoc}
     * @param value {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
     @Override
    public void setShort(long offset, short value) {
        super.durableSetShort(offset, value);
    }

    /**
     * {@inheritDoc}
     * @param offset {@inheritDoc}
     * @param value {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void setInt(long offset, int value) {
        super.durableSetInt(offset, value);
    }

    /**
     * {@inheritDoc}
     * @param offset {@inheritDoc}
     * @param value {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void setLong(long offset, long value) {
        super.durableSetLong(offset, value);
    }

    /**
     * {@inheritDoc}  
     * @param src {@inheritDoc}
     * @param srcOffset {@inheritDoc}
     * @param dstOffset {@inheritDoc}
     * @param length {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc} 
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void copyFrom(MemoryAccessor src, long srcOffset, long dstOffset, long length) {
        super.durableCopy(src, srcOffset, dstOffset, length);
    }

    /**
     * {@inheritDoc} 
     * @param srcArray {@inheritDoc}
     * @param srcOffset {@inheritDoc}
     * @param dstOffset {@inheritDoc}
     * @param length {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc}
     * @throws IllegalStateException {@inheritDoc}
     */
    @Override
    public void copyFromArray(byte[] srcArray, int srcOffset, long dstOffset, int length) {
        super.durableCopyFromArray(srcArray, srcOffset, dstOffset, length);
    }

    /**
     *{@inheritDoc}
     * @param value {@inheritDoc}
     * @param offset {@inheritDoc}
     * @param length {@inheritDoc}
     * @throws IndexOutOfBoundsException {@inheritDoc} 
     * @throws IllegalStateException {@inheritDoc}
     */    
    @Override
    public void setMemory(byte value, long offset, long length) {
        super.durableSetMemory(value, offset, length);
    }

    /**
    * Deallocates the memory this accessor references.
    * @param transactional {@inheritDoc}
    * @throws HeapException if the memory could not be deallocated
    */
    public void freeMemory(boolean transactional) {
        checkValid();
        heap().freeMemory(directAddress(), transactional);
        super.reset();
    }

    public void freeMemory() {
        freeMemory(false);
    }

    /**
     * Checks that the range of bytes from {@code offset} (inclusive) to {@code offset} + length (exclusive) 
     * is within the bounds of this accessor. 
     * @param offset The start of the range to check
     * @param length The number of bytes in the range to check
     * @throws IndexOutOfBoundsException if the range is not within this accessor's bounds
     */
    void checkBounds(long offset, long length) {
        super.checkBounds(offset, length);
    }

    @Override
    long metadataSize() { 
        return METADATA_SIZE; 
    }

    /**
     * Returns the allocated size, in bytes, of the memory referenced by this accessor.  
     * @return the allocated size, in bytes, of the memory referenced by this accessor 
     */
    @Override
    public long size() { 
        return super.size(); 
    }

    /**
     * Sets this accessor's handle thereby changing the memory that this accessor references.   
     * @param handle The handle to use
     * @throws IllegalArgumentException if {@code handle} is not valid
     * @throws HeapException if the accessor could not be updated
     */
    public void handle(long handle) {
        heap().checkBounds(handle, METADATA_SIZE);
        super.handle(handle, true);
    }

    /**
     * Resets this accessor to its initial state. In its initial state the accessor refers
     * to no memory and is not usable until it is assigned a handle using {@link com.intel.pmem.llpl.Accessor#handle}
     */
    public void resetHandle() {
        super.reset();
    }

    /**
     * Executes the supplied {@code Function}, passing in a {@code Range} object suitable for durably modifying bytes in 
     * the specified range of offsets within this accessor's memory. Writing using the Range object in ranged operations
     * such as this can be more efficient than calling separate write methods on the accessor. 
     * @param startOffset the starting offset of the range
     * @param rangeLength the number of bytes in the range
     * @param op the function to execute
     * @param <T> the return type of the supplied function
     * @return the object returned from the supplied function 
     * @throws IndexOutOfBoundsException if the the specified range of bytes is not within this accessor's bounds
     * @throws TransactionException if a transaction was not active and a new transaction could not be created
     */    
    public <T> T withRange(long startOffset, long rangeLength, Function<Range, T> op) {
        return super.durableWithRange(startOffset, rangeLength, op);
    }
    
    /**
     * Executes the supplied {@code Consumer} function, passing in a {@code Range} object
     * suitable for durably modifying bytes in the specified range of offsets within this accessor's memory.
     * Writing using the Range object in ranged operations such as this can be more efficient than
     * calling separate write methods on the accessor. 
     * @param startOffset the starting offset of the range
     * @param rangeLength the number of bytes in the range
     * @param op the function to execute
     * @throws IndexOutOfBoundsException if the the specified range of bytes is not within this accessor's bounds
     * @throws TransactionException if a transaction was not active and a new transaction could not be created
     */    
    public void withRange(long startOffset, long rangeLength, Consumer<Range> op) {
        super.durableWithRange(startOffset, rangeLength, (Range r) -> {op.accept(r); return (Void)null;});
    }

    /**
     * Executes the suppied {@code Consumer}, passing in a {@code Range} object suitable for durably modifying bytes 
     * within this accessor's memory. Upon uninterrupted return of this method, all writes done using the supplied Range 
     * object will be durable. Writing using the Range object in ranged operations such as this can be more efficient than
     * calling separate write methods on the accessor. 
     * @param op the function to execute
     */    
   public void withRange(Consumer<Range> op) {
        super.durableWithRange(0, size(), (Range r) -> {op.accept(r); return (Void)null;}); 
    }

    /**
     * Transactionally executes the supplied {@code Function}, passing in a {@code Range} object suitable for modifying bytes in 
     * the specified range of offsets within this accessor's memory.  
     * @param startOffset the starting offset of the range
     * @param rangeLength the number of bytes in the range
     * @param op the function to execute
     * @param <T> the return type of the supplied function
     * @return the object returned from the supplied function
     * @throws IndexOutOfBoundsException if the the specified range of bytes is not within this accessor's bounds
     * @throws TransactionException if a transaction was not active and a new transaction could not be created
     */    
    public <T> T transactionalWithRange(long startOffset, long rangeLength, Function<Range, T> op) {
        return super.transactionalWithRange(startOffset, rangeLength, op);
    }

    /**
     * Transactionally executes the supplied {@code Consumer} function, passing in a {@code Range} object
     * suitable for modifying bytes in the specified range of offsets within this accessor's memory.
     * Writing using the Range object in ranged operations such as this can be more efficient than
     * calling separate write methods on the accessor. 
     * @param startOffset the starting offset of the range
     * @param rangeLength the number of bytes in the range
     * @param op the function to execute
     * @throws IndexOutOfBoundsException if the the specified range of bytes is not within this accessor's bounds
     * @throws TransactionException if a transaction was not active and a new transaction could not be created
     */    
    public void transactionalWithRange(long startOffset, long rangeLength, Consumer<Range> op) {
        super.transactionalWithRange(startOffset, rangeLength, (Range r) -> {op.accept(r); return (Void)null;}); 
    }

    /**
     * Tansactionally executes the supplied {@code Consumer} function, passing in a {@code Range} object suitable for modifying bytes 
     * within this accessor's memory. Writing using the Range object in ranged operations such as this can be more efficient than
     * calling separate write methods on the accessor. 
     * @param op the function to execute
     * @throws TransactionException if a transaction was not active and a new transaction could not be created
     */    
    public void transactionalWithRange(Consumer<Range> op) {
         transactionalWithRange(0, size(), op); 
    }
}
