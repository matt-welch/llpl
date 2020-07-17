/*
 * Copyright (C) 2018-2019 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *
 */

package com.intel.pmem.llpl;

import java.io.File;
import java.util.function.Supplier;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Manages a heap of memory.
   Can be used for volatile and persistent applications.<br><br>  
 *
 * Heap {@code createHeap()} factory methods accept a {@code String} path argument that specifies the identity of the heap and an optional
 * {@code long} size argument. There are 5 ways to configure the size of a heap:<br><br>
 * 1. fixed size -- the path argument is a file path and a supplied size arugument sets both the minimum and 
 * maximum size of the heap.<br>
 * 2. growable -- the path argument is a file path and the heap size starts with size {@code MINIMUM_HEAP_SIZE}, growing 
 * in size as needed up to the available memory.<br>
 * 3. growable with limit -- the path argument is a file path and the heap size starts with size {@code MINIMUM_HEAP_SIZE},
 * growing in size as needed up to a maximum size set by the supplied size argument.<br>
 * 4. DAX device -- the path argument is DAX device name and the size of the dax device sets both the minimum and
 * maximum size of the heap.<br>
 * 5. fused memory pool -- the path argument points to a memory pool configuration file that describes DAX
 * devices [EXPERIMENTAL] or file systems to be fused for use with a single heap.  The combined memory sizes
 * of devices or file systems sets both the minimum and maximum size of the heap.<br>  
 * 
 * @since 1.0
 *
 * @see com.intel.pmem.llpl.AnyHeap   
 */
public final class Heap extends AnyHeap {
    static final String HEAP_LAYOUT_ID = "llpl_heap";

    private Heap(String path, long size) {
        super(path, size);
    }
 
    private Heap(String path) {
        super(path);
    }
 
    /**
     * Creates a new heap. If {@code path} refers to a directory, a 
     * growable heap will be created.  If {@code path} refers to a DAX device, a heap over that 
     * entire device will be created.  
     * @param path a path to the new heap
     * @return the heap at the specified path
     * @throws IllegalArgumentException if {@code path} is {@code null}
     * @throws HeapException if the heap could not be created
     */
    public static synchronized Heap createHeap(String path) {
        if (path == null) throw new IllegalArgumentException("The provided path must not be null");

        String heapPath;
        if (path.startsWith("/dev/dax")) {
            heapPath = path;
        }
	    else { // Default case: growable heap with no limit
            File file = new File(path);
            if (!file.exists() || !file.isDirectory()) {
                throw new HeapException("The path \"" + path + "\" doesnt exist or is not a directory");
            }
            heapPath = (new File(file, AnyHeap.POOL_SET_FILE)).getAbsolutePath();
            try {
                AnyHeap.createPoolSetFile(file, 0);
            } 
            catch (IOException e) {
                throw new HeapException(e.getMessage());
            }
        }
        if (AnyHeap.getHeap(heapPath))
            throw new HeapException("Heap \"" + path + "\" already exists.");

        Heap heap = new Heap(heapPath, 0);
        AnyHeap.putHeap(heapPath, heap);
        return heap;
    }

    /**
     * Creates a new heap. If {@code path} refers to a file, a fixed-size heap of {@code size} bytes will be created.
     * If {@code path} refers to a directory, a growable heap, limited to {@code size} bytes, will be created.
     * If {@code size} is {@code 0}, the path will be interpreted as an advanced "fused pool" descriptor file.
     * @param path the path to the heap
     * @param size the number of bytes to allocate for the heap
     * @return the heap at the specified path
     * @throws IllegalArgumentException if {@code path} is {@code null} or if {@code size} 
     * is less than {@code MINIMUM_HEAP_SIZE}
     * @throws HeapException if the heap could not be created
     */
    public static synchronized Heap createHeap(String path, long size) {
        if (path == null) throw new IllegalArgumentException("The provided path must not be null.");
        if (path.startsWith("/dev/dax")) throw new IllegalArgumentException("The path is invalid for this method");
        if (size != 0L && size < MINIMUM_HEAP_SIZE)
            throw new HeapException("The Heap size must be at least " + MINIMUM_HEAP_SIZE + " bytes.");
        File file = new File(path);

        String heapPath;
        long heapSize;
        // Advanced fused case 
        // Size must be 0 and path is an existing poolSetFile
        if (size == 0) {
            if (file.exists() && file.isFile()) {
                heapPath = path;
                heapSize = size;
            } 
            else throw new HeapException("The path \"" + path + "\" does not exist or is not a file.");
        } 
        else if (file.exists() && file.isDirectory()) { //growable with limit
            heapPath = (new File(file, AnyHeap.POOL_SET_FILE)).getAbsolutePath();
            try {
                AnyHeap.createPoolSetFile(file, size);
            } 
            catch (IOException e) {
                throw new HeapException(e.getMessage());
            }
            heapSize = 0L;
        } 
        else { //Fixed Heap
            if (file.exists()) {
                throw new HeapException("Heap \"" + path + "\" already exists.");
            }
            heapPath = path;
            heapSize = size;
        }
        if (AnyHeap.getHeap(heapPath))
            throw new HeapException("Heap \"" + path + "\" already exists.");

        Heap heap = new Heap(heapPath, heapSize);
        AnyHeap.putHeap(heapPath, heap);
        return heap;
    }

    /**
     * Opens an existing heap. Provides access to the heap associated with the specified {@code path}.
     * @param path the path to the heap
     * @return the heap at the specified path
     * @throws IllegalArgumentException if {@code path} is {@code null}
     * @throws HeapException if the heap could not be opened
     */
    public static synchronized Heap openHeap(String path) {
        if (path == null) throw new IllegalArgumentException("The provided path must not be null.");
        Heap heap = (Heap)AnyHeap.getHeap(path, getHeapClass("Heap"));
        String heapPath = path;
        if (heap == null) {
            File file = new File(path);
            if (file.exists() && file.isDirectory()) {
                heapPath = new File(file, AnyHeap.POOL_SET_FILE).getAbsolutePath();
                heap = (Heap)AnyHeap.getHeap(heapPath, getHeapClass("Heap"));
                if (heap != null) return heap;
            }
            heap = new Heap(heapPath);
            AnyHeap.putHeap(heapPath, heap);
        }
        return heap;
    }

    /**
     * Creates a new {@code Accessor}. In its initial state the accessor refers
     * to no memory and is not usable until it is assigned a handle using {@link com.intel.pmem.llpl.Accessor#handle}
     * @return the new accessor object 
     */
    public Accessor createAccessor() {
        return new Accessor(this);
    }

    /**
     * Creates a new {@code CompactAccessor}. In its initial state the accessor refers
     * to no memory and is not usable until it is assigned a handle using {@link com.intel.pmem.llpl.CompactAccessor#handle}
     * @return the new accessor object 
     */
    public CompactAccessor createCompactAccessor() {
        return new CompactAccessor(this);
    }

    /**
    * Allocates memory of {@code size} bytes. The allocation may be done transactionally or non-transactionally.
    * @param size the number of bytes to allocate
    * @param transactional true if the allocation should be done transactionally
    * @return a handle to the allocated memory 
    * @throws HeapException if the memory could not be allocated
    */
    public long allocateMemory(long size, boolean transactional) {
        long allocationSize = size + MemoryBlock.METADATA_SIZE; 
        Supplier<Long> body = () -> {
            long handle =  transactional ? allocateTransactional(allocationSize) : allocateAtomic(allocationSize);
            if (handle == 0) throw new HeapException("Failed to allocate memory of size " + size);
            AnyHeap.UNSAFE.putLong(poolHandle() + handle + AnyMemoryBlock.SIZE_OFFSET, size);
            return handle;
        };
        long handle = transactional ? new Transaction(this).run(body) : body.get();
        return handle;
    }

    public long allocateMemory(long size) {
        return allocateMemory(size, false);
    }

    /**
    * Allocates memory of {@code size} bytes. The allocation may be done transactionally or non-transactionally.
    * @param size the number of bytes to allocate
    * @param transactional true if the allocation should be done transactionally
    * @return a handle to the allocated memory 
    * @throws HeapException if the memory could not be allocated
    */
    public long allocateCompactMemory(long size, boolean transactional) {
        long handle =  transactional ? Transaction.create(this, () -> allocateTransactional(size)) : allocateAtomic(size);
        if (handle == 0) throw new HeapException("Failed to allocate memory of size " + size);
        return handle;
    }

    public long allocateCompactMemory(long size) {
        return allocateCompactMemory(size, false);
    }

    /**
    * Allocates a memory block of {@code size} bytes. The allocation may be done transactionally or non-transactionally.
    * @param size the size of the memory block in bytes
    * @param transactional true if the allocation should be done transactionally
    * @return the allocated memory block 
    * @throws HeapException if the memory block could not be allocated
    */
    public MemoryBlock allocateMemoryBlock(long size, boolean transactional) {
        //checkValid();
        return new MemoryBlock(this, size, transactional);
    }

    public MemoryBlock allocateMemoryBlock(long size) {
        //checkValid();
        return new MemoryBlock(this, size, false);
    }

    public MemoryBlock allocateMemoryBlock(long size, Consumer<Range> initializer) {
        return allocateMemoryBlock(size, false, initializer);
    }

    /**
    * Allocates a memory block of {@code size} bytes. The allocation may be done transactionally or non-transactionally.
    * The supplied initializer function is executed, passing a Range object that can be used to 
    * write within the memory block's range of bytes.  Allocating a memory block with an initializer
    * function can be more efficient than separate allocation and initialization. 
    * @param size the size of the memory block in bytes
    * @param transactional true if the allocation should be done transactionally
    * @param initializer a function to be run to initialize the new memory block
    * @return the allocated memory block 
    * @throws HeapException if the memory block could not be allocated
    */
    public MemoryBlock allocateMemoryBlock(long size, boolean transactional, Consumer<Range> initializer) {
        MemoryBlock block = new MemoryBlock(this, size, transactional);
        Range range = block.range();
        initializer.accept(range);
        range.markInvalid();
        return block;
    }

    /**
    * Allocates a compact memory block of {@code size} bytes. The allocation is done atomically.
    * The supplied initializer function is executed, passing a Range object that can be used to 
    * write within the memory block's range of bytes.  Allocating a memory block with an initializer
    * function can be more efficient than separate allocation and initialization. 
    * @param size the size of the memory block in bytes
    * @param initializer a function to be run to initialize the new memory block
    * @return the allocated memory block 
    * @throws HeapException if the memory block could not be allocated
    */
    public CompactMemoryBlock allocateCompactMemoryBlock(long size, Consumer<Range> initializer) {
        return allocateCompactMemoryBlock(size, false, initializer);    
    }

    /**
    * Allocates a compact memory block of {@code size} bytes. The allocation may be done transactionally or non-transactionally.
    * The supplied initializer function is executed, passing a Range object that can be used to 
    * write within the memory block's range of bytes.  Allocating a memory block with an initializer
    * function can be more efficient than separate allocation and initialization. 
    * @param size the size of the memory block in bytes
    * @param transactional true if the allocation should be done transactionally
    * @param initializer a function to be run to initialize the new memory block
    * @return the allocated memory block 
    * @throws HeapException if the memory block could not be allocated
    */
    public CompactMemoryBlock allocateCompactMemoryBlock(long size, boolean transactional, Consumer<Range> initializer) {
        CompactMemoryBlock block = new CompactMemoryBlock(this, size, transactional);
        Range range = block.range(0, size);
        initializer.accept(range);
        range.markInvalid();
        return block;
    }

    /**
    * Allocates a compact memory block of {@code size} bytes. The allocation may be done transactionally or non-transactionally.
    * @param size the size of the memory block in bytes
    * @param transactional true if the allocation should be done transactionally
    * @return the allocated memory block 
    * @throws HeapException if the memory block could not be allocated
    */
    public CompactMemoryBlock allocateCompactMemoryBlock(long size, boolean transactional) {
        return new CompactMemoryBlock(this, size, transactional);
    }

    /**
    * Allocates a compact memory block of {@code size} bytes. The allocation is done atomically.
    * @param size the size of the memory block in bytes
    * @return the allocated memory block 
    * @throws HeapException if the memory block could not be allocated
    */
    public CompactMemoryBlock allocateCompactMemoryBlock(long size) {
        return new CompactMemoryBlock(this, size, false);
    }

    /**
    * Returns a previously-allocated memory block associated with the given handle.
    * @param handle the handle of a previously-allocated memory block
    * @return the memory block associated with the given handle
    * @throws IllegalArgumentException if {@code handle} is not valid
    * @throws HeapException if the memory block could not be created
    */
    @Override
    public MemoryBlock memoryBlockFromHandle(long handle) {
        checkBounds(handle, MemoryBlock.METADATA_SIZE);
        return new MemoryBlock(this, poolHandle(), handle);
    }

    public void execute(Runnable op) {
        op.run();
    }

    public <T> T execute(Supplier<T> op) {
        return op.get();
    }

    @Override
    CompactMemoryBlock internalMemoryBlockFromHandle(long handle) {
        //checkValid();
        return new CompactMemoryBlock(this, poolHandle(), handle);
    }

    @Override
    String getHeapLayoutID() {
        return HEAP_LAYOUT_ID;
    }
	
    /**
    * Returns a previously-allocated compact memory block associated with the given handle.
    * @param handle the handle of a previously-allocated memory block
    * @return the compact memory block associated with the given handle
    * @throws IllegalArgumentException if {@code handle} is not valid
    */
    public CompactMemoryBlock compactMemoryBlockFromHandle(long handle) {
        checkBounds(handle);
        return new CompactMemoryBlock(this, poolHandle(), handle);
    }
}
