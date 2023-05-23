import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// STUDENT ID - 38556804
public class MemoryMgmt {

    private List<ListOfMemoryChunks> translationTable = new ArrayList<ListOfMemoryChunks>();

    public static void main(String args[]) throws Exception {
        MemoryMgmt myMemoryManager = new MemoryMgmt(13);
        // myMemoryManager.printMalloc(8191);
        // myMemoryManager.statusPrint();
        myMemoryManager.print();
    }

    public MemoryMgmt(int power2) {
        translationTable.add(new ListOfMemoryChunks((int) Math.pow(2, power2), 0, null));
    }

    // malloc() and the private methods it calls:

    public int malloc(int size) throws Exception {
        if (size < 0)
            throw new Exception("the value for \"size\" cannot be a negative value.");
        // Tries to find the best-fit to allocate memory of size "size". If it
        // finds a memory chunk, it allocates the memory and returns a pointer.
        FreeMemory bestFit = getBestFit(size);
        if (bestFit != null) {
            for (ListOfMemoryChunks tempList : translationTable) {
                if (listContainsFreeChunk(bestFit, tempList)) {
                    int address = allocateChunk(size, bestFit, tempList);
                    return address;
                }
            }
        }
        // If it couldn't find a best-fit, that means that there is no space available,
        // therefore it requests more memory to "sbrk()" and tries to allocate the
        // memory again with the returned address from "sbrk()".
        int allocatedAddress = sbrk(size + 24); // Is the size of a header of a free chunk of memory.
        bestFit = getFreeMemoryFromAddress(allocatedAddress); // this should call the exception.
        if (bestFit != null) {
            for (ListOfMemoryChunks tempList : translationTable) {
                if (addressInSpace(allocatedAddress, tempList)) {
                    int address = allocateChunk(size, bestFit, tempList);
                    return address;
                }
            }
        }
        throw new Exception("malloc() was unable to allocate memory aftel calling sbrk().");
        // This part of the code should not be accessed, as in every case
        // "getFreeMemoryFromAddress()" would return null an Exception is thrown.
    }

    private FreeMemory getBestFit(int size) {
        // Runs through the free list and looks for the FreeChunk which's available
        // space is the closest to the one passed by "size". If there aren't any
        // "FreeMemory" which's available size is at least "size", then it returns null.
        FreeMemory temp = ListOfMemoryChunks.headFreeList;
        FreeMemory bestFit = null;
        int neededSize = size + 8;
        int availableSize;
        while (temp != null) {
            availableSize = temp.length + temp.headerSize;
            if (availableSize >= neededSize) {
                if (bestFit == null)
                    bestFit = temp;
                else if (temp.length < bestFit.length)
                    bestFit = temp;
            }
            temp = temp.nextFree;
        }
        return bestFit;
    }

    private boolean listContainsFreeChunk(FreeMemory chunk, ListOfMemoryChunks theList) {
        for (ChunkMemory temp : theList.listChunksMemory) {
            if (temp == chunk)
                return true;
        }
        return false;
    }

    private int getAddressFromChunk(ChunkMemory chunk, ListOfMemoryChunks theList) {
        // This method generates a chunk's 'virtual' address by running through every
        // element of the list adding and looking for a match between temp and chunk.
        int tempAddress = theList.baseAddress;
        for (ChunkMemory temp : theList.listChunksMemory) {
            tempAddress += temp.headerSize;
            if (temp == chunk)
                return tempAddress;
            tempAddress += temp.length;
        }
        return -1;
    }

    private int allocateChunk(int size, FreeMemory FreeMemory, ListOfMemoryChunks theList) throws Exception {
        // This method takes a "FreeMemory" contained in "theList" where a "UsedMemory"
        // of length "size" is to be created. It removes the current "FreeMemory",
        // creates an "UsedMemory" that takes its place and afterwards creates a new
        // "FreeMemory" if there is enough space for its header.
        UsedMemory newChunk = new UsedMemory(FreeMemory.isPreviousFree, FreeMemory.previousLength,
                false, size);
        int index = theList.listChunksMemory.indexOf(FreeMemory);
        if (index == -1)
            throw new Exception(
                    "The passed value for \"FreeMemory\" is not contained in \"theList\". Unable to match.");

        theList.listChunksMemory.remove(index);
        theList.listChunksMemory.add(index, newChunk);
        // If there is space create a new "FreeMemory", or unusable chunk.
        int leftOverMemory = FreeMemory.length + FreeMemory.headerSize - (newChunk.length + newChunk.headerSize);
        if (leftOverMemory >= 24) {
            FreeMemory newFree = new FreeMemory(false, size, true,
                    FreeMemory.length - (size + newChunk.headerSize),
                    FreeMemory.nextFree, FreeMemory.previousFree);
            if (FreeMemory.nextFree != null)
                FreeMemory.nextFree.previousFree = newFree;
            if (FreeMemory.previousFree != null)
                FreeMemory.previousFree.nextFree = newFree;
            theList.listChunksMemory.add(index + 1, newFree);
            if (ListOfMemoryChunks.headFreeList == FreeMemory)
                ListOfMemoryChunks.headFreeList = newFree;
        } else if (leftOverMemory > 0) {
            UsedMemory unusable = new UsedMemory(false, newChunk.length, false, leftOverMemory - 8);
            theList.listChunksMemory.add(unusable);
            setChunkToUnusable(unusable, theList.listChunksMemory.indexOf(unusable), theList);
            if (ListOfMemoryChunks.headFreeList == FreeMemory)
                ListOfMemoryChunks.headFreeList = FreeMemory.nextFree;
        }
        if (ListOfMemoryChunks.headFreeList == FreeMemory)
            ListOfMemoryChunks.headFreeList = FreeMemory.nextFree;
        else if (FreeMemory.previousFree != null && FreeMemory.nextFree != null)
            FreeMemory.previousFree.nextFree = FreeMemory.nextFree;
        return getAddressFromChunk(newChunk, theList);
    }

    private FreeMemory getFreeMemoryFromAddress(int address) throws Exception {
        ListOfMemoryChunks MemoryContAddress = null;
        for (ListOfMemoryChunks tempMemory : translationTable) {
            if (addressInSpace(address, tempMemory))
                MemoryContAddress = tempMemory;
        }
        if (MemoryContAddress == null) {
            throw new Exception("The address returned by \"sbrk()\" is not existant.");
        }
        int tempAddress = MemoryContAddress.baseAddress;
        for (ChunkMemory tempChunks : MemoryContAddress.listChunksMemory) {
            tempAddress += tempChunks.headerSize;
            if (tempAddress == address) {
                if (tempChunks instanceof FreeMemory) {
                    return (FreeMemory) tempChunks;
                } else
                    throw new Exception("The address returned by \"sbrk()\" is not existant.");
            }
        }
        throw new Exception("The address returned by \"sbrk()\" is not existant.");
    }

    // sbrk() and the private methods it calls:

    public int sbrk(int size) {
        if (size < 32)
            size = 31;
        // The minimum memory sbrk() should allocate is 32, as it is the minimum power
        // of 2 which can fit the header of a free chunk of memory.
        int sizeRequired = ((int) Math.pow(2, smallestPowerTwo(size)));
        int baseAddress = findAvailableMemoryAddress(sizeRequired);
        ListOfMemoryChunks temp = new ListOfMemoryChunks(sizeRequired, baseAddress, ListOfMemoryChunks.headFreeList);
        translationTable.add(temp);
        FreeMemory tailFreeList = ListOfMemoryChunks.headFreeList;
        while (tailFreeList.nextFree != null)
            tailFreeList = tailFreeList.nextFree;
        tailFreeList.nextFree = ((FreeMemory) temp.listChunksMemory.get(0));
        return baseAddress + temp.listChunksMemory.get(0).headerSize;
    }

    private int smallestPowerTwo(int number) {
        float result = number;
        int power = 0;
        while (result > 1) {
            result = result / 2;
            power++;
        }
        if (result == 1)
            return power + 1;
        return power;
    }

    private int findAvailableMemoryAddress(int size) {
        /*
         * In a real world scenario the system would look for available memory of size
         * "size" at this point. But, since this is a simulation, it will just generate
         * a random value for the base address of this newly found 'free'
         * "ListOfMemoryChunks".
         */
        Random rand = new Random();
        int boundAddress = translationTable.get(translationTable.size() - 1).boundAddress;
        int baseAddress = boundAddress + rand.nextInt(5000);
        return baseAddress;
    }

    // free() and the private methods it calls:

    public void free(int ptr) throws Exception {
        for (ListOfMemoryChunks temp : translationTable) {
            if (addressInSpace(ptr, temp)) {
                FreeMemoryFromList(ptr, temp);
                return;
            }
        }
        throw new Exception("Free has been called for an unallocated memory address.");
    }

    private void FreeMemoryFromList(int address, ListOfMemoryChunks theList) throws Exception {
        // This method gets a chunk that is represented by an address, ff it is not
        // used, it throws an exception, otherwise it sets it to unusable or free.
        ChunkMemory tempChunk = getChunkFromAddress(address);
        if (tempChunk == null || !(tempChunk instanceof UsedMemory))
            throw new Exception("Free has been called for an unallocated memory address.");
        int index = theList.listChunksMemory.indexOf(tempChunk);
        if (tempChunk.length + tempChunk.headerSize < 24)
            // If there isn't enough memory to store the header of a free chunk:
            setChunkToUnusable((UsedMemory) tempChunk, index, theList);
        else
            setChunkToFree((UsedMemory) tempChunk, index, theList);
    }

    private void setChunkToUnusable(UsedMemory currentChunk, int index, ListOfMemoryChunks theList) {
        theList.listChunksMemory.remove(index);
        MemoryHole newUnusable = new MemoryHole(currentChunk);
        theList.listChunksMemory.add(index, newUnusable);
    }

    private void setChunkToFree(UsedMemory currentChunk, int index, ListOfMemoryChunks theList) {
        // This method replaces a used chunk with a free one and combines it with
        // adjecent free chunks if there are any.

        /* MIRAR */FreeMemory previousFree = findPreviousFreeChunk(index, theList);
        // Necessary to update free list.
        /* MIRAR */ FreeMemory nextFree = findNextFreeChunk(index, theList); // Necessary to update free list.
        theList.listChunksMemory.remove(index);
        FreeMemory newFree = new FreeMemory(currentChunk.isPreviousFree, currentChunk.previousLength, true,
                currentChunk.length - 16, nextFree, previousFree);
        /* MIRAR */ fixFreeList(previousFree, newFree, nextFree);
        theList.listChunksMemory.add(index, newFree);
        coalescingCheck(index, theList);
    }

    private FreeMemory findPreviousFreeChunk(int index, ListOfMemoryChunks theList) {
        ChunkMemory temp;
        while (index >= 0) {
            temp = theList.listChunksMemory.get(index);
            if (temp instanceof FreeMemory)
                return (FreeMemory) temp;
            index--;
        }
        // If the previous free chunk is not in the current ListOfMemoryChunks, it
        // recursively
        // looks at the previous ListOfMemoryChunks until reaching the beginning of the
        // list.
        if (translationTable.indexOf(theList) != 0) {
            ListOfMemoryChunks previousEntry = translationTable.get(translationTable.indexOf(theList) - 1);
            return findPreviousFreeChunk(previousEntry.listChunksMemory.size() - 1, previousEntry);
        }
        return null;
    }

    private FreeMemory findNextFreeChunk(int index, ListOfMemoryChunks theList) {
        ChunkMemory temp;
        while (index < theList.listChunksMemory.size()) {
            temp = theList.listChunksMemory.get(index);
            if (temp instanceof FreeMemory)
                return (FreeMemory) temp;
            index++;
        }
        // If the next free chunk is not in the current ListOfMemoryChunks, it
        // recursively
        // looks at the next ListOfMemoryChunks until reaching the end of the list.
        if (translationTable.indexOf(theList) != translationTable.size() - 1) {
            ListOfMemoryChunks nextEntry = translationTable.get(translationTable.indexOf(theList) + 1);
            return findNextFreeChunk(nextEntry.listChunksMemory.size() - 1, nextEntry);
        }
        return null;
    }

    private void fixFreeList(FreeMemory previous, FreeMemory newlyCreated, FreeMemory next) {
        if (previous == null)
            ListOfMemoryChunks.headFreeList = newlyCreated;
        else
            previous.nextFree = newlyCreated;
        if (next != null)
            next.previousFree = newlyCreated;
    }

    private void coalescingCheck(int index, ListOfMemoryChunks theList) {
        // "CheckAdjecentLeft()" will combine the chunk at "index" to the one before it
        // if they are both free. "CheckAdjecentRight()" will the same but with the
        // chunk after "index".
        checkAdjecentRight(index, theList);
        checkAdjecentLeft(index, theList);
    }

    private void checkAdjecentLeft(int index, ListOfMemoryChunks theList) {
        // Checks for index out of bounds.
        if (index - 1 < 0)
            return;
        ChunkMemory tempPrevious = theList.listChunksMemory.get(index - 1);
        // Checks if the previous chunk of memory is free.
        if (!tempPrevious.isFree)
            return;
        FreeMemory previous = (FreeMemory) tempPrevious;
        FreeMemory current = (FreeMemory) theList.listChunksMemory.get(index);
        theList.listChunksMemory.remove(index);
        theList.listChunksMemory.remove(index - 1);
        FreeMemory temp = new FreeMemory(previous.isPreviousFree, previous.previousLength, true,
                previous.length + current.length + current.headerSize,
                current.nextFree, previous.previousFree);
        // If the previous chunk of memory the current one is combining with was the the
        // head of the free list, then the head of the free list updates its pointer to
        // the newly created chunk.
        if (previous == ListOfMemoryChunks.headFreeList)
            ListOfMemoryChunks.headFreeList = temp;
        theList.listChunksMemory.add(index - 1, temp);
        if (previous.previousFree != null)
            previous.previousFree.nextFree = temp;
        if (current.nextFree != null)
            current.nextFree.previousFree = temp;
        return;
    }

    private void checkAdjecentRight(int index, ListOfMemoryChunks theList) {
        // Checks for index out of bounds.
        if (index + 1 >= theList.listChunksMemory.size())
            return;
        ChunkMemory tempNext = theList.listChunksMemory.get(index + 1);
        // Checks if the next chunk of memory is free.
        if (!tempNext.isFree)
            return;
        FreeMemory current = (FreeMemory) theList.listChunksMemory.get(index);
        FreeMemory next = (FreeMemory) tempNext;
        theList.listChunksMemory.remove(index);
        theList.listChunksMemory.remove(index);
        FreeMemory temp = new FreeMemory(current.isPreviousFree, current.previousLength, true,
                current.length + next.length + current.headerSize, next.nextFree,
                current.previousFree);
        // If head of the free list was pointing at the free chunk we are combining with
        // the next free chunk, then the head of the free list is updated to point at
        // the newly created chunk.
        if (current == ListOfMemoryChunks.headFreeList)
            ListOfMemoryChunks.headFreeList = temp;
        theList.listChunksMemory.add(index, temp);
        if (next.nextFree != null)
            next.nextFree.previousFree = temp;
        if (current.previousFree != null)
            current.previousFree.nextFree = temp;
        return;
    }

    // print() and the private methods it calls:

    public void print() {
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
        test7();
        test8();
        test9();
    }

    private void test1() {
        System.out.println(
                "\n========================================================================================================");
        System.out.println("Running test number 1...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request 28 bytes of memory -> Store a string -> Retrieve the string -> Free memory.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(1);
        int pointer = printMalloc(28);
        if (pointer == -1)
            return;
        printStoreString(pointer, "Testing");
        statusPrint();
        printRetrieveString(pointer);
        printFreeing(pointer);
        statusPrint();
        System.out.println(
                "========================================================================================================");
    }

    private void test2() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 2...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request 16 bytes of memory -> Try to store a string larger than 16 bytes -> Retrieve string -> Free memory.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(2);
        int pointer = printMalloc(16);
        if (pointer == -1)
            return;
        printStoreString(pointer, "LargerStringLargerString");
        statusPrint();
        printRetrieveString(pointer);
        printFreeing(pointer);
        statusPrint();
        System.out.println(
                "========================================================================================================");
    }

    private void test3() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 3...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request in order: 28 bytes, 1024 bytes, 28 bytes -> Free the 1024 bytes -> Request 512 bytes -> Free all.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(3);
        int pointer1 = printMalloc(28);
        if (pointer1 == -1)
            return;
        int pointer2 = printMalloc(1024);
        if (pointer2 == -1)
            return;
        int pointer3 = printMalloc(28);
        if (pointer3 == -1)
            return;
        statusPrint();
        if (!printFreeing(pointer2))
            return;
        statusPrint();
        int pointer4 = printMalloc(512);
        if (pointer4 == -1)
            return;
        statusPrint();
        if (!printFreeing(pointer1))
            return;
        if (!printFreeing(pointer3))
            return;
        if (!printFreeing(pointer4))
            return;
        statusPrint();
        System.out.println(
                "========================================================================================================");
    }

    private void test4() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 4...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request 7168 bytes -> Request 1024 bytes -> Free all chunks.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(4);
        int pointer1 = printMalloc(7168);
        if (pointer1 == -1)
            return;
        int pointer2 = printMalloc(1024);
        if (pointer2 == -1)
            return;
        statusPrint();
        if (!printFreeing(pointer1))
            return;
        if (!printFreeing(pointer2))
            return;
        statusPrint();
        System.out.println(
                "========================================================================================================");
    }

    private void test5() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 5...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request 1024 bytes -> Request 28 bytes -> Free the 28 bytes -> Free the 28 bytes again.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(5);
        int pointer1 = printMalloc(1024);
        if (pointer1 == -1)
            return;
        int pointer2 = printMalloc(28);
        if (pointer2 == -1)
            return;
        statusPrint();
        if (!printFreeing(pointer2))
            return;
        statusPrint();
        if (!printFreeing(pointer2))
            return;
        System.out.println(
                "========================================================================================================");
    }

    private void test6() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 6...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request 4 bytes -> Store a integer -> Retrieve an integer -> Retrieve a string -> Free the 4 bytes.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(6);
        int pointer = printMalloc(4);
        if (pointer == -1)
            return;
        statusPrint();
        System.out.print("Storing int: \"" + 987 + "\" at " + addressFormater(pointer) + "...");
        try {
            setDataAddress(987, pointer);
            System.out.println("the entire int has been stored.");
        } catch (Exception e) {
            System.out.println(
                    "\nExpected EXCEPTION CAUGHT. Data \"LargerStringLargerString\" cannot be fully stored at "
                            + addressFormater(pointer)
                            + ".");
        }
        System.out.print("\nRetrieving string from " + addressFormater(pointer) + "...");
        try {
            System.out.println(" the int retrieved from memory is: \"" + getIntFromAddress(pointer) + "\".\n");
        } catch (Exception e) {
            System.out
                    .println(
                            "The pointer specified to get an Integer from does not point to a allocated memory chunk or memory hasn't been assigned.\n");
        }
        printRetrieveString(pointer);
        printFreeing(pointer);
        statusPrint();
        System.out.println(
                "========================================================================================================");
    }

    private void test7() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 7...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Request 10 bytes -> Free the 10 bytes -> Request 10 bytes -> Free memory.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(7);
        int pointer = printMalloc(10);
        if (pointer == -1)
            return;
        statusPrint();
        printFreeing(pointer);
        statusPrint();
        int pointer2 = printMalloc(10);
        if (pointer2 == -1)
            return;
        statusPrint();
        printFreeing(pointer2);
        statusPrint();
    }

    private void test8() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 8...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Allocate 8184 bytes - > Free it -> Allocate 8184 bytes again -> Free memory.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(8);
        statusPrint();
        int pointer = printMalloc(8184);
        if (pointer == -1)
            return;
        statusPrint();
        printFreeing(pointer);
        statusPrint();
        pointer = printMalloc(8184);
        if (pointer == -1)
            return;
        statusPrint();
        printFreeing(pointer);
        statusPrint();
    }

    private void test9() {
        System.out.println(
                "\n\n========================================================================================================");
        System.out.println("Running test number 9...");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------");
        System.out.println(
                "Allocate (8184-9) bytes . - > Free it -> Try to allocate 8184 bytes -> Free memory.");
        System.out.println(
                "--------------------------------------------------------------------------------------------------------\n");
        printHeadPointer(9);
        statusPrint();
        int pointer = printMalloc(8184 - 9);
        if (pointer == -1)
            return;
        statusPrint();
        printFreeing(pointer);
        statusPrint();
        pointer = printMalloc(8184);
        if (pointer == -1)
            return;
        statusPrint();
        printFreeing(pointer);
        statusPrint();
    }

    private void printHeadPointer(int testNumber) {
        ListOfMemoryChunks theList = null;
        for (ListOfMemoryChunks temp : translationTable) {
            if (listContainsFreeChunk(ListOfMemoryChunks.headFreeList, temp))
                theList = temp;
        }
        if (theList == null) {
            System.out.println("There has been a problem generating the head pointer of the free list for test number "
                    + testNumber + ".");
            return;
        }
        System.out.println(
                "HEAD of free-list pointer : "
                        + addressFormater(getAddressFromChunk(ListOfMemoryChunks.headFreeList, theList))
                        + "\n");
    }

    private boolean printFreeing(int pointer) {
        System.out.print("Freeing allocated memory at " + addressFormater(pointer) + "...");
        try {
            free(pointer);
        } catch (Exception e) {
            System.out.println("EXCEPTION CAUGHT. Free has been called for an unallocated memory address.");
            System.out.println("Interrupting test.");
            return false;
        }
        System.out.println("memory freed.\n");
        return true;
    }

    private void printStoreString(int pointer, String theString) {
        String pointerString = addressFormater(pointer);
        System.out.print("Storing string: \"" + theString + "\" at " + pointerString + "...");
        try {
            if (!setDataAddress(theString, pointer)) {
                System.out.println(
                        "the given data \"" + theString + "\" cannot be fully stored at " + pointerString + ".");
            }
        } catch (Exception e) {
            System.out.println("the entire string has been stored.");
        }
    }

    private void printRetrieveString(int pointer) {
        String pointerString = addressFormater(pointer);
        System.out.print("\nRetrieving string from " + pointerString + "...");
        try {
            System.out.println(" the String retrieved from memory is: \"" + getStringFromAddress(pointer) + "\".\n");
        } catch (Exception e) {
            System.out
                    .println(
                            "The pointer specified to get an String from does not point to a allocated memory chunk.\n");
        }
    }

    private int printMalloc(int size) {
        System.out.print("Requesting " + size + " bytes of memory...");
        int pointer = 0;
        try {
            pointer = malloc(size);
            System.out.println(" memory allocated.");
        } catch (Exception e) {
            System.out.println("Unexpected EXCEPTION CAUGHT at malloc call. Test will be stopped.");
            return -1;
        }
        System.out.println("Pointer: " + addressFormater(pointer) + "\n");
        return pointer;
    }

    private void statusPrint() {
        // This method prints a table of the internal state of the memory.
        System.out.println("The allocated memory consists of:");
        for (ListOfMemoryChunks temp : translationTable) {
            System.out.println("\n· Memory Space with base address: " + addressFormater(temp.baseAddress)
                    + " and bound address: " + addressFormater(temp.boundAddress) + ":\n");
            System.out.println("     Chunk   ||  Header Address  ||  Memory Address  ||   Number bytes");
            System.out.println(
                    "  -----------------------------------------------------------------------");
            printIndividualChunks(temp);
        }
        System.out.println();
    }

    private void printIndividualChunks(ListOfMemoryChunks oneSpace) {
        int count = 0;
        int address = oneSpace.baseAddress;
        for (ChunkMemory temp : oneSpace.listChunksMemory) {
            System.out.print("  - Chunk[" + count + "] ||");
            System.out.print("    " + addressFormater(address) + "    ||    " +
                    addressFormater(temp.headerSize + address) + "    ||   " + temp.length);
            if (temp.isFree && temp instanceof FreeMemory)
                System.out.println(" FREE");
            else if (temp instanceof MemoryHole)
                System.out.println(" UNUSABLE");
            else
                System.out.println(" USED");
            count++;
            address += temp.length + temp.headerSize;
        }

    }

    private void setDataAddress(int data, int address) throws Exception {
        UsedMemory aChunkOfMemory = getUsedChunkFromAddress(address);
        aChunkOfMemory.setData(data);
    }

    private boolean setDataAddress(String data, int address) throws Exception {
        UsedMemory aChunkOfMemory = getUsedChunkFromAddress(address);
        return aChunkOfMemory.setData(data);
    }

    private String getStringFromAddress(int address) throws Exception {
        UsedMemory aChunkOfMemory = getUsedChunkFromAddress(address);
        return aChunkOfMemory.getStringData();
    }

    private int getIntFromAddress(int address) throws Exception {
        UsedMemory aChunkOfMemory = getUsedChunkFromAddress(address);
        return aChunkOfMemory.getIntData();
    }

    private UsedMemory getUsedChunkFromAddress(int address) throws Exception {
        ListOfMemoryChunks theList = null;
        for (ListOfMemoryChunks temp : translationTable) {
            if (addressInSpace(address, temp))
                theList = temp;
        }
        if (theList == null) {
            throw new Exception("There is no allocated memory at the given address to \"storeAtMemory()\".");
        }
        ChunkMemory theChunk = getChunkFromAddress(address);
        if (theChunk == null || !(theChunk instanceof UsedMemory)) {
            throw new Exception("There is no allocated memory at the given address to \"storeAtMemory()\".");
        } else
            return ((UsedMemory) theChunk);
    }

    // Private methods used by multiple methods.
    private boolean addressInSpace(int address, ListOfMemoryChunks theList) {
        if (address < theList.baseAddress || address > theList.boundAddress)
            return false;
        return true;
    }

    private ListOfMemoryChunks searchListInTranslationTable(int address) throws Exception {
        for (ListOfMemoryChunks tempList : translationTable) {
            if (addressInSpace(address, tempList))
                return tempList;
        }
        throw new Exception("The given address does not point to allocated memory.");
    }

    private ChunkMemory getChunkFromAddress(int address) throws Exception {
        // This method does the 'reverse' as "getAddressFromChunk()".
        ListOfMemoryChunks theList = searchListInTranslationTable(address);
        int tempAddress = theList.baseAddress;
        for (ChunkMemory temp : theList.listChunksMemory) {
            tempAddress += temp.headerSize;
            if (tempAddress == address)
                return temp;
            tempAddress += temp.length;
        }
        throw new Exception("There is not an allocated chunk of memory at the given address.");
    }

    private String addressFormater(int address) {
        return String.format("%010d", address);
    }
}

class ListOfMemoryChunks {

    List<ChunkMemory> listChunksMemory = new ArrayList<ChunkMemory>();
    static FreeMemory headFreeList;
    int baseAddress;
    int boundAddress;

    ListOfMemoryChunks(int sizeBytes, int baseAddress, FreeMemory headFreeList) {
        ListOfMemoryChunks.headFreeList = headFreeList;
        this.baseAddress = baseAddress;
        boundAddress = this.baseAddress + sizeBytes;
        FreeMemory temp = new FreeMemory(false, 0, true, sizeBytes - 24, null, null);
        listChunksMemory.add(temp);
        if (ListOfMemoryChunks.headFreeList == null) {
            ListOfMemoryChunks.headFreeList = temp;
        }
    }
}

abstract class ChunkMemory {

    public int headerSize;
    public boolean isFree;
    public int length;
    public boolean isPreviousFree;
    public int previousLength;

    ChunkMemory(int headerSize, boolean isFree, int length, boolean isPreviousFree, int previousLength) {
        this.isFree = isFree;
        this.headerSize = headerSize;
        if (length >= 0)
            this.length = length;
        else {
            this.length = 0;
            System.out.println(
                    "A chunk's length cannot be negative. Length will be set to 0 by default.");
        }
        this.isPreviousFree = isPreviousFree;
        this.previousLength = previousLength;
    }
}

class MemoryHole extends ChunkMemory {
    MemoryHole(UsedMemory chunk) {
        super(chunk.headerSize, false, chunk.length, chunk.isPreviousFree, chunk.previousLength);
    }
}

class UsedMemory extends ChunkMemory {

    public int bytes[];

    UsedMemory(boolean previousFree, int previousLength, boolean free, int length) {
        super(8, free, length, previousFree, previousLength);
        bytes = new int[length];
    }

    public void setData(int data) {
        int sizeIntKB = Integer.SIZE / 8;
        if (sizeIntKB > this.length) {
            System.out.println("The given data cannot be assigned as it exceeds the allocated memory limits.");
            return;
        }
        this.bytes[1] = data;
    }

    public boolean setData(String data) {
        int sizeCharacterB = Character.SIZE / 8;
        int sizeStringB = sizeCharacterB * (data.length() + 1);
        if (sizeStringB > this.bytes.length) {
            for (int i = 0; i != this.bytes.length / 2 - 1; i++) {
                this.bytes[i] = (int) (data.charAt(i));
            }
            this.bytes[this.bytes.length / 2] = (int) '\0';
        } else {
            for (int i = 0; i <= data.length(); i++) {
                this.bytes[i] = (int) (data.charAt(i));
            }
            this.bytes[data.length()] = (int) '\0';
        }
        if (sizeStringB > this.bytes.length) {
            return false;
        }
        return true;
    }

    public int getIntData() throws Exception {
        try {
            return bytes[1];
        } catch (Exception e) {
            throw new Exception("Data hasn't been assigned.");
        }
    }

    public String getStringData() {
        int i = 0;
        String result = "";
        while (bytes[i] != ((int) '\0')) {
            result = result + ((char) (bytes[i]));
            i++;
            if (i == bytes.length - 1)
                return result;
        }
        return result;
    }

}

class FreeMemory extends ChunkMemory {

    public FreeMemory nextFree;
    public FreeMemory previousFree;

    FreeMemory(boolean isPreviousFree, int previousLength, boolean isFree, int length,
            FreeMemory nextFree,
            FreeMemory previousFree) {
        super(24, isFree, length, isPreviousFree, previousLength);
        this.nextFree = nextFree;
        this.previousFree = previousFree;
    }

    public int getLength() {
        return length;
    }
}
