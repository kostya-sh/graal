/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.tools.jaotc.binformat;

import static org.graalvm.compiler.hotspot.meta.HotSpotAOTProfilingPlugin.Options.TieredAOT;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.tools.jaotc.binformat.Symbol.Binding;
import jdk.tools.jaotc.binformat.Symbol.Kind;
import jdk.tools.jaotc.binformat.elf.JELFRelocObject;
import jdk.tools.jaotc.binformat.macho.JMachORelocObject;
import jdk.tools.jaotc.binformat.pecoff.JPECoffRelocObject;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.options.OptionValues;

/**
 * A format-agnostic container class that holds various components of a binary.
 *
 * <p>
 * This class holds information necessary to create platform-specific binary containers such as
 * ELFContainer for Linux and Solaris operating systems or MachOContainer for Mac OS or PEContainer
 * for MS Windows operating systems.
 *
 * <p>
 * Method APIs provided by this class are used to construct and populate platform-independent
 * contents of a binary as the first step to create a binary representation of code generated by a
 * compiler backend such as Graal.
 *
 * <p>
 * Methods to record and access code section contents, symbols and relocations are provided.
 */
public final class BinaryContainer implements SymbolTable {
    private final OptionValues graalOptions;

    private final int codeSegmentSize;

    private final int codeEntryAlignment;

    private final boolean threadLocalHandshakes;

    /**
     * Container holding code bits and any other related information.
     */
    private final CodeContainer codeContainer;

    /**
     * Container holding global offset data for hotspot linkage.
     */
    private final ByteContainer extLinkageGOTContainer;

    /**
     * Patched by HotSpot, contains Klass pointers.
     */
    private final ByteContainer klassesGotContainer;

    /**
     * Patched by HotSpot, contains MethodCounters pointers.
     */
    private final ByteContainer countersGotContainer;

    /**
     * Patched lazily by hotspot, contains klass/method pointers.
     */
    private final ByteContainer metadataGotContainer;

    /**
     * BSS container, contains method state array.
     */
    private final ByteContainer methodStateContainer;

    /**
     * Patched by hotspot, contains java object pointers.
     */
    private final ByteContainer oopGotContainer;

    // Containers holding read-only data
    private final ReadOnlyDataContainer configContainer;
    private final ReadOnlyDataContainer metaspaceNamesContainer;
    private final ReadOnlyDataContainer methodsOffsetsContainer;
    private final ReadOnlyDataContainer klassesOffsetsContainer;
    private final ReadOnlyDataContainer klassesDependenciesContainer;
    private final HeaderContainer headerContainer;
    private final ReadOnlyDataContainer stubsOffsetsContainer;
    private final ReadOnlyDataContainer codeSegmentsContainer;

    // This cannot be read only since we need to patch the metadata at runtime..
    private final ReadOnlyDataContainer methodMetadataContainer;

    /**
     * Container containing constant data used by code.
     */
    private final ReadOnlyDataContainer constantDataContainer;

    /**
     * Map holding the Strings table.
     */
    private final Map<String, Integer> offsetStringTable = new HashMap<>();

    private final Map<String, Integer> metaspaceNames = new HashMap<>();

    // List of relocation table entries - (symbolName, relocationInfo)
    private final Map<String, Symbol> symbolTable = new HashMap<>();
    private final Map<Symbol, List<Relocation>> relocationTable = new HashMap<>();
    private final Map<Symbol, Relocation> uniqueRelocationTable = new HashMap<>();

    /**
     * Mapping of local VM function names to known global symbols generated in the output binary.
     */
    private static final HashMap<String, String> functionNamesToAOTSymbols = new HashMap<>();

    private static final String[][] map = {
//@formatter:off
        {"CompilerToVM::Data::SharedRuntime_deopt_blob_unpack",        "_aot_deopt_blob_unpack"},
        {"CompilerToVM::Data::SharedRuntime_deopt_blob_uncommon_trap", "_aot_deopt_blob_uncommon_trap"},
        {"CompilerToVM::Data::SharedRuntime_ic_miss_stub",             "_aot_ic_miss_stub"},
        {"CompilerToVM::Data::SharedRuntime_handle_wrong_method_stub", "_aot_handle_wrong_method_stub"},
        {"SharedRuntime::exception_handler_for_return_address",        "_aot_exception_handler_for_return_address"},
        {"SharedRuntime::register_finalizer",                          "_aot_register_finalizer"},
        {"SharedRuntime::OSR_migration_end",                           "_aot_OSR_migration_end"},
        {"CompilerRuntime::resolve_dynamic_invoke",                    "_aot_resolve_dynamic_invoke"},
        {"CompilerRuntime::resolve_string_by_symbol",                  "_aot_resolve_string_by_symbol"},
        {"CompilerRuntime::resolve_klass_by_symbol",                   "_aot_resolve_klass_by_symbol"},
        {"CompilerRuntime::resolve_method_by_symbol_and_load_counters","_aot_resolve_method_by_symbol_and_load_counters"},
        {"CompilerRuntime::initialize_klass_by_symbol",                "_aot_initialize_klass_by_symbol"},
        {"CompilerRuntime::invocation_event",                          "_aot_invocation_event"},
        {"CompilerRuntime::backedge_event",                            "_aot_backedge_event"},

        {"CompilerToVM::Data::dpow", "_aot_shared_runtime_dpow"},
        {"CompilerToVM::Data::dexp", "_aot_shared_runtime_dexp"},
        {"CompilerToVM::Data::dcos", "_aot_shared_runtime_dcos"},
        {"CompilerToVM::Data::dsin", "_aot_shared_runtime_dsin"},
        {"CompilerToVM::Data::dtan", "_aot_shared_runtime_dtan"},
        {"CompilerToVM::Data::dlog", "_aot_shared_runtime_dlog"},
        {"CompilerToVM::Data::dlog10", "_aot_shared_runtime_dlog10"},

        {"StubRoutines::_jbyte_arraycopy", "_aot_stub_routines_jbyte_arraycopy"},
        {"StubRoutines::_jshort_arraycopy", "_aot_stub_routines_jshort_arraycopy"},
        {"StubRoutines::_jint_arraycopy", "_aot_stub_routines_jint_arraycopy"},
        {"StubRoutines::_jlong_arraycopy", "_aot_stub_routines_jlong_arraycopy"},
        {"StubRoutines::_oop_arraycopy", "_aot_stub_routines_oop_arraycopy"},
        {"StubRoutines::_oop_arraycopy_uninit", "_aot_stub_routines_oop_arraycopy_uninit"},

        {"StubRoutines::_jbyte_disjoint_arraycopy", "_aot_stub_routines_jbyte_disjoint_arraycopy"},
        {"StubRoutines::_jshort_disjoint_arraycopy", "_aot_stub_routines_jshort_disjoint_arraycopy"},
        {"StubRoutines::_jint_disjoint_arraycopy", "_aot_stub_routines_jint_disjoint_arraycopy"},
        {"StubRoutines::_jlong_disjoint_arraycopy", "_aot_stub_routines_jlong_disjoint_arraycopy"},
        {"StubRoutines::_oop_disjoint_arraycopy", "_aot_stub_routines_oop_disjoint_arraycopy"},
        {"StubRoutines::_oop_disjoint_arraycopy_uninit", "_aot_stub_routines_oop_disjoint_arraycopy_uninit"},

        {"StubRoutines::_arrayof_jbyte_arraycopy", "_aot_stub_routines_arrayof_jbyte_arraycopy"},
        {"StubRoutines::_arrayof_jshort_arraycopy", "_aot_stub_routines_arrayof_jshort_arraycopy"},
        {"StubRoutines::_arrayof_jint_arraycopy", "_aot_stub_routines_arrayof_jint_arraycopy"},
        {"StubRoutines::_arrayof_jlong_arraycopy", "_aot_stub_routines_arrayof_jlong_arraycopy"},
        {"StubRoutines::_arrayof_oop_arraycopy", "_aot_stub_routines_arrayof_oop_arraycopy"},
        {"StubRoutines::_arrayof_oop_arraycopy_uninit", "_aot_stub_routines_arrayof_oop_arraycopy_uninit"},

        {"StubRoutines::_arrayof_jbyte_disjoint_arraycopy", "_aot_stub_routines_arrayof_jbyte_disjoint_arraycopy"},
        {"StubRoutines::_arrayof_jshort_disjoint_arraycopy", "_aot_stub_routines_arrayof_jshort_disjoint_arraycopy"},
        {"StubRoutines::_arrayof_jint_disjoint_arraycopy", "_aot_stub_routines_arrayof_jint_disjoint_arraycopy"},
        {"StubRoutines::_arrayof_jlong_disjoint_arraycopy", "_aot_stub_routines_arrayof_jlong_disjoint_arraycopy"},
        {"StubRoutines::_arrayof_oop_disjoint_arraycopy", "_aot_stub_routines_arrayof_oop_disjoint_arraycopy"},
        {"StubRoutines::_arrayof_oop_disjoint_arraycopy_uninit", "_aot_stub_routines_arrayof_oop_disjoint_arraycopy_uninit"},

        {"StubRoutines::_unsafe_arraycopy", "_aot_stub_routines_unsafe_arraycopy"},

        {"StubRoutines::_checkcast_arraycopy", "_aot_stub_routines_checkcast_arraycopy"},

        {"StubRoutines::_generic_arraycopy", "_aot_stub_routines_generic_arraycopy"},

        {"StubRoutines::_aescrypt_encryptBlock", "_aot_stub_routines_aescrypt_encryptBlock"},
        {"StubRoutines::_aescrypt_decryptBlock", "_aot_stub_routines_aescrypt_decryptBlock"},
        {"StubRoutines::_cipherBlockChaining_encryptAESCrypt", "_aot_stub_routines_cipherBlockChaining_encryptAESCrypt"},
        {"StubRoutines::_cipherBlockChaining_decryptAESCrypt", "_aot_stub_routines_cipherBlockChaining_decryptAESCrypt"},
        {"StubRoutines::_updateBytesCRC32", "_aot_stub_routines_update_bytes_crc32"},
        {"StubRoutines::_crc_table_adr", "_aot_stub_routines_crc_table_adr"},

        {"StubRoutines::_sha1_implCompress", "_aot_stub_routines_sha1_implCompress" },
        {"StubRoutines::_sha1_implCompressMB", "_aot_stub_routines_sha1_implCompressMB" },
        {"StubRoutines::_sha256_implCompress", "_aot_stub_routines_sha256_implCompress" },
        {"StubRoutines::_sha256_implCompressMB", "_aot_stub_routines_sha256_implCompressMB" },
        {"StubRoutines::_sha512_implCompress", "_aot_stub_routines_sha512_implCompress" },
        {"StubRoutines::_sha512_implCompressMB", "_aot_stub_routines_sha512_implCompressMB" },
        {"StubRoutines::_multiplyToLen", "_aot_stub_routines_multiplyToLen" },

        {"StubRoutines::_counterMode_AESCrypt", "_aot_stub_routines_counterMode_AESCrypt" },
        {"StubRoutines::_ghash_processBlocks", "_aot_stub_routines_ghash_processBlocks" },
        {"StubRoutines::_crc32c_table_addr", "_aot_stub_routines_crc32c_table_addr" },
        {"StubRoutines::_updateBytesCRC32C", "_aot_stub_routines_updateBytesCRC32C" },
        {"StubRoutines::_updateBytesAdler32", "_aot_stub_routines_updateBytesAdler32" },
        {"StubRoutines::_squareToLen", "_aot_stub_routines_squareToLen" },
        {"StubRoutines::_mulAdd", "_aot_stub_routines_mulAdd" },
        {"StubRoutines::_montgomeryMultiply", "_aot_stub_routines_montgomeryMultiply" },
        {"StubRoutines::_montgomerySquare", "_aot_stub_routines_montgomerySquare" },
        {"StubRoutines::_vectorizedMismatch", "_aot_stub_routines_vectorizedMismatch" },

        {"StubRoutines::_throw_delayed_StackOverflowError_entry", "_aot_stub_routines_throw_delayed_StackOverflowError_entry" },


        {"os::javaTimeMillis", "_aot_os_javaTimeMillis"},
        {"os::javaTimeNanos", "_aot_os_javaTimeNanos"},

        {"JVMCIRuntime::monitorenter", "_aot_jvmci_runtime_monitorenter"},
        {"JVMCIRuntime::monitorexit", "_aot_jvmci_runtime_monitorexit"},
        {"JVMCIRuntime::object_notify", "_aot_object_notify"},
        {"JVMCIRuntime::object_notifyAll", "_aot_object_notifyAll"},
        {"JVMCIRuntime::log_object", "_aot_jvmci_runtime_log_object"},
        {"JVMCIRuntime::log_printf", "_aot_jvmci_runtime_log_printf"},
        {"JVMCIRuntime::vm_message", "_aot_jvmci_runtime_vm_message"},
        {"JVMCIRuntime::new_instance", "_aot_jvmci_runtime_new_instance"},
        {"JVMCIRuntime::log_primitive", "_aot_jvmci_runtime_log_primitive"},
        {"JVMCIRuntime::new_multi_array", "_aot_jvmci_runtime_new_multi_array"},
        {"JVMCIRuntime::validate_object", "_aot_jvmci_runtime_validate_object"},
        {"JVMCIRuntime::dynamic_new_array", "_aot_jvmci_runtime_dynamic_new_array"},
        {"JVMCIRuntime::write_barrier_pre", "_aot_jvmci_runtime_write_barrier_pre"},
        {"JVMCIRuntime::identity_hash_code", "_aot_jvmci_runtime_identity_hash_code"},
        {"JVMCIRuntime::write_barrier_post", "_aot_jvmci_runtime_write_barrier_post"},
        {"JVMCIRuntime::dynamic_new_instance", "_aot_jvmci_runtime_dynamic_new_instance"},
        {"JVMCIRuntime::thread_is_interrupted", "_aot_jvmci_runtime_thread_is_interrupted"},
        {"JVMCIRuntime::exception_handler_for_pc", "_aot_jvmci_runtime_exception_handler_for_pc"},
        {"JVMCIRuntime::test_deoptimize_call_int", "_aot_jvmci_runtime_test_deoptimize_call_int"},

        {"JVMCIRuntime::throw_and_post_jvmti_exception",      "_aot_jvmci_runtime_throw_and_post_jvmti_exception"},
        {"JVMCIRuntime::throw_klass_external_name_exception", "_aot_jvmci_runtime_throw_klass_external_name_exception"},
        {"JVMCIRuntime::throw_class_cast_exception",          "_aot_jvmci_runtime_throw_class_cast_exception"},

        {"JVMCIRuntime::vm_error", "_aot_jvmci_runtime_vm_error"},
        {"JVMCIRuntime::new_array", "_aot_jvmci_runtime_new_array"}
        //@formatter:on
    };

    static {
        for (String[] entry : map) {
            functionNamesToAOTSymbols.put(entry[0], entry[1]);
        }
    }

    /**
     * Allocates a {@code BinaryContainer} object whose content will be generated in a file with the
     * prefix {@code prefix}. It also initializes internal code container, symbol table and
     * relocation tables.
     *
     * @param graalOptions
     */
    public BinaryContainer(OptionValues graalOptions, GraalHotSpotVMConfig graalHotSpotVMConfig, GraphBuilderConfiguration graphBuilderConfig, String jvmVersion) {
        this.graalOptions = graalOptions;

        this.codeSegmentSize = graalHotSpotVMConfig.codeSegmentSize;
        if (codeSegmentSize < 1 || codeSegmentSize > 1024) {
            throw new InternalError("codeSegmentSize is not in range [1, 1024] bytes: (" + codeSegmentSize + "), update JPECoffRelocObject");
        }
        if ((codeSegmentSize & (codeSegmentSize - 1)) != 0) {
            throw new InternalError("codeSegmentSize is not power of 2: (" + codeSegmentSize + "), update JPECoffRelocObject");
        }

        this.codeEntryAlignment = graalHotSpotVMConfig.codeEntryAlignment;

        this.threadLocalHandshakes = graalHotSpotVMConfig.threadLocalHandshakes;

        // Section unique name is limited to 8 characters due to limitation on Windows.
        // Name could be longer but only first 8 characters are stored on Windows.

        // read only, code
        codeContainer = new CodeContainer(".text", this);

        // read only, info
        headerContainer = new HeaderContainer(jvmVersion, new ReadOnlyDataContainer(".header", this));
        configContainer = new ReadOnlyDataContainer(".config", this);
        metaspaceNamesContainer = new ReadOnlyDataContainer(".meta.names", this);
        methodsOffsetsContainer = new ReadOnlyDataContainer(".meth.offsets", this);
        klassesOffsetsContainer = new ReadOnlyDataContainer(".kls.offsets", this);
        klassesDependenciesContainer = new ReadOnlyDataContainer(".kls.dependencies", this);

        stubsOffsetsContainer = new ReadOnlyDataContainer(".stubs.offsets", this);
        codeSegmentsContainer = new ReadOnlyDataContainer(".code.segments", this);
        constantDataContainer = new ReadOnlyDataContainer(".meth.constdata", this);
        methodMetadataContainer = new ReadOnlyDataContainer(".meth.metadata", this);

        // writable sections
        oopGotContainer = new ByteContainer(".oop.got", this);
        klassesGotContainer = new ByteContainer(".kls.got", this);
        countersGotContainer = new ByteContainer(".cnt.got", this);
        metadataGotContainer = new ByteContainer(".meta.got", this);
        methodStateContainer = new ByteContainer(".meth.state", this);
        extLinkageGOTContainer = new ByteContainer(".got.linkage", this);

        addGlobalSymbols();

        recordConfiguration(graalHotSpotVMConfig, graphBuilderConfig);
    }

    private void recordConfiguration(GraalHotSpotVMConfig graalHotSpotVMConfig, GraphBuilderConfiguration graphBuilderConfig) {
        // @formatter:off
        boolean[] booleanFlags = { graalHotSpotVMConfig.cAssertions, // Debug VM
                                   graalHotSpotVMConfig.useCompressedOops,
                                   graalHotSpotVMConfig.useCompressedClassPointers,
                                   graalHotSpotVMConfig.compactFields,
                                   graalHotSpotVMConfig.useG1GC,
                                   graalHotSpotVMConfig.useTLAB,
                                   graalHotSpotVMConfig.useBiasedLocking,
                                   TieredAOT.getValue(graalOptions),
                                   graalHotSpotVMConfig.enableContended,
                                   graalHotSpotVMConfig.restrictContended,
                                   graphBuilderConfig.omitAssertions(),
                                   graalHotSpotVMConfig.threadLocalHandshakes
        };

        int[] intFlags         = { graalHotSpotVMConfig.getOopEncoding().getShift(),
                                   graalHotSpotVMConfig.getKlassEncoding().getShift(),
                                   graalHotSpotVMConfig.contendedPaddingWidth,
                                   graalHotSpotVMConfig.fieldsAllocationStyle,
                                   1 << graalHotSpotVMConfig.logMinObjAlignment(),
                                   graalHotSpotVMConfig.codeSegmentSize,
        };
        // @formatter:on

        byte[] booleanFlagsAsBytes = flagsToByteArray(booleanFlags);
        int size0 = configContainer.getByteStreamSize();

        // @formatter:off
        int computedSize = booleanFlagsAsBytes.length * Byte.BYTES    + // size of boolean flags
                           intFlags.length            * Integer.BYTES + // size of int flags
                           Integer.BYTES;                               // size of the "computedSize"

        configContainer.appendInt(computedSize).
                        appendInts(intFlags).
                        appendBytes(booleanFlagsAsBytes);
        // @formatter:on

        int size = configContainer.getByteStreamSize() - size0;
        assert size == computedSize;
    }

    private static byte[] flagsToByteArray(boolean[] flags) {
        byte[] byteArray = new byte[flags.length];
        for (int i = 0; i < flags.length; ++i) {
            byteArray[i] = boolToByte(flags[i]);
        }
        return byteArray;
    }

    private static byte boolToByte(boolean flag) {
        return (byte) (flag ? 1 : 0);
    }

    /**
     * Free some memory.
     */
    public void freeMemory() {
        offsetStringTable.clear();
        metaspaceNames.clear();
    }

    /*
     * Global symbol names in generated DSO corresponding to VM's symbols. VM needs to look up this
     * symbol in DSO and link it with VM's corresponding symbol: store VM's symbol address or value
     * in the named GOT cell.
     */

    public static String getCardTableAddressSymbolName() {
        return "_aot_card_table_address";
    }

    public static String getHeapTopAddressSymbolName() {
        return "_aot_heap_top_address";
    }

    public static String getHeapEndAddressSymbolName() {
        return "_aot_heap_end_address";
    }

    public static String getCrcTableAddressSymbolName() {
        return "_aot_stub_routines_crc_table_adr";
    }

    public static String getPollingPageSymbolName() {
        return "_aot_polling_page";
    }

    public static String getResolveStaticEntrySymbolName() {
        return "_resolve_static_entry";
    }

    public static String getResolveVirtualEntrySymbolName() {
        return "_resolve_virtual_entry";
    }

    public static String getResolveOptVirtualEntrySymbolName() {
        return "_resolve_opt_virtual_entry";
    }

    public static String getNarrowKlassBaseAddressSymbolName() {
        return "_aot_narrow_klass_base_address";
    }

    public static String getNarrowOopBaseAddressSymbolName() {
        return "_aot_narrow_oop_base_address";
    }

    public static String getLogOfHeapRegionGrainBytesSymbolName() {
        return "_aot_log_of_heap_region_grain_bytes";
    }

    public static String getInlineContiguousAllocationSupportedSymbolName() {
        return "_aot_inline_contiguous_allocation_supported";
    }

    public int getCodeSegmentSize() {
        return codeSegmentSize;
    }

    public int getCodeEntryAlignment() {
        return codeEntryAlignment;
    }

    public boolean getThreadLocalHandshakes() {
        return threadLocalHandshakes;
    }

    /**
     * Gets the global AOT symbol associated with the function name.
     *
     * @param functionName function name
     * @return AOT symbol for the given function name, or null if there is no mapping.
     */
    public static String getAOTSymbolForVMFunctionName(String functionName) {
        return functionNamesToAOTSymbols.get(functionName);
    }

    private void addGlobalSymbols() {
        // Create global symbols for all containers.
        createContainerSymbol(codeContainer);
        createContainerSymbol(configContainer);
        createContainerSymbol(methodsOffsetsContainer);
        createContainerSymbol(klassesOffsetsContainer);
        createContainerSymbol(klassesDependenciesContainer);
        createContainerSymbol(klassesGotContainer);
        createContainerSymbol(countersGotContainer);
        createContainerSymbol(metadataGotContainer);
        createContainerSymbol(methodStateContainer);
        createContainerSymbol(oopGotContainer);
        createContainerSymbol(metaspaceNamesContainer);
        createContainerSymbol(methodMetadataContainer);
        createContainerSymbol(stubsOffsetsContainer);
        createContainerSymbol(headerContainer.getContainer());
        createContainerSymbol(codeSegmentsContainer);

        createGotSymbol(getResolveStaticEntrySymbolName());
        createGotSymbol(getResolveVirtualEntrySymbolName());
        createGotSymbol(getResolveOptVirtualEntrySymbolName());
        createGotSymbol(getCardTableAddressSymbolName());
        createGotSymbol(getHeapTopAddressSymbolName());
        createGotSymbol(getHeapEndAddressSymbolName());
        createGotSymbol(getNarrowKlassBaseAddressSymbolName());
        createGotSymbol(getNarrowOopBaseAddressSymbolName());
        createGotSymbol(getPollingPageSymbolName());
        createGotSymbol(getLogOfHeapRegionGrainBytesSymbolName());
        createGotSymbol(getInlineContiguousAllocationSupportedSymbolName());

        for (HashMap.Entry<String, String> entry : functionNamesToAOTSymbols.entrySet()) {
            createGotSymbol(entry.getValue());
        }
    }

    /**
     * Creates a global symbol of the form {@code "A" + container name}. Note, linker on Windows
     * does not allow names which start with '.'
     *
     * @param container container to create a symbol for
     */
    private static void createContainerSymbol(ByteContainer container) {
        container.createSymbol(0, Kind.OBJECT, Binding.GLOBAL, 0, "A" + container.getContainerName());
    }

    /**
     * Creates a global GOT symbol of the form {@code "got." + name}.
     *
     * @param name name for the GOT symbol
     */
    private void createGotSymbol(String name) {
        String s = "got." + name;
        Symbol gotSymbol = extLinkageGOTContainer.createGotSymbol(s);
        extLinkageGOTContainer.createSymbol(gotSymbol.getOffset(), Kind.OBJECT, Binding.GLOBAL, 8, name);
    }

    /**
     * Create a platform-specific binary file representing the content of the
     * {@code BinaryContainer} object.
     *
     * This method is called after creating and performing any necessary changes to the contents of
     * code stream, symbol tables and relocation tables is completely finalized
     *
     * @param outputFileName name of output file
     *
     * @throws IOException in case of file creation failure
     */
    public void createBinary(String outputFileName) throws IOException {
        String osName = System.getProperty("os.name");
        switch (osName) {
            case "Linux":
            case "SunOS":
                JELFRelocObject elfobj = JELFRelocObject.newInstance(this, outputFileName);
                elfobj.createELFRelocObject(relocationTable, symbolTable.values());
                break;
            case "Mac OS X":
                JMachORelocObject machobj = new JMachORelocObject(this, outputFileName);
                machobj.createMachORelocObject(relocationTable, symbolTable.values());
                break;
            default:
                if (osName.startsWith("Windows")) {
                    JPECoffRelocObject pecoffobj = new JPECoffRelocObject(this, outputFileName);
                    pecoffobj.createPECoffRelocObject(relocationTable, symbolTable.values());
                    break;
                } else
                    throw new InternalError("Unsupported platform: " + osName);
        }
    }

    /**
     * Add symbol to the symbol table. If the existing symbol is undefined and the specified symbol
     * is not undefined, replace the existing symbol information with that specified.
     *
     * @param symInfo symbol information to be added
     */
    @Override
    public void addSymbol(Symbol symInfo) {
        if (symInfo.getName().startsWith("got.") && !(symInfo instanceof GotSymbol)) {
            throw new InternalError("adding got. without being GotSymbol");
        }
        if (symbolTable.containsKey(symInfo.getName())) {
            throw new InternalError("Symbol: " + symInfo.getName() + " already exists in SymbolTable");
        } else {
            // System.out.println("# Symbol [" + name + "] [" + symInfo.getValue() + "] [" +
            // symInfo.getSection().getContainerName() + "] [" + symInfo.getSize() + "]");
            symbolTable.put(symInfo.getName(), symInfo);
        }
    }

    public boolean addStringOffset(String name, Integer offset) {
        offsetStringTable.put(name, offset);
        return true;
    }

    /**
     * Add relocation entry for {@code symName}. Multiple relocation entries for a given symbol may
     * exist.
     *
     * @param info relocation information to be added
     */
    public void addRelocation(Relocation info) {
        // System.out.println("# Relocation [" + info.getSymbol() + "] [" + info.getOffset() + "] ["
        // +
        // info.getSection().getContainerName() + "] [" + info.getSymbol().getName() + "] [" +
        // info.getSymbol().getOffset() + " @ " + info.getSymbol().getSection().getContainerName() +
        // "]");
        if (relocationTable.containsKey(info.getSymbol())) {
            relocationTable.get(info.getSymbol()).add(info);
        } else if (uniqueRelocationTable.containsKey(info.getSymbol())) {
            // promote
            ArrayList<Relocation> list = new ArrayList<>(2);
            list.add(uniqueRelocationTable.get(info.getSymbol()));
            list.add(info);
            relocationTable.put(info.getSymbol(), list);
            uniqueRelocationTable.remove(info.getSymbol());
        } else {
            uniqueRelocationTable.put(info.getSymbol(), info);
        }
    }

    /**
     * Get symbol with name {@code symName}.
     *
     * @param symName name of symbol for which symbol table information is being queried
     * @return success or failure of insertion operation
     */
    @Override
    public Symbol getSymbol(String symName) {
        return symbolTable.get(symName);
    }

    @Override
    public Symbol createSymbol(int offset, Kind kind, Binding binding, int size, String name) {
        if (kind != Kind.NATIVE_FUNCTION) {
            throw new UnsupportedOperationException("Must be external functions: " + name);
        }
        Symbol symbol = new Symbol(offset, kind, binding, null, size, name);
        addSymbol(symbol);
        return symbol;
    }

    /**
     * Get offset in got section with name {@code symName}.
     *
     * @param name for which String table information is being queried
     * @return success or failure of insertion operation
     */
    public Integer getStringOffset(String name) {
        return offsetStringTable.get(name);
    }

    /**
     * Insert {@code targetCode} to code stream with {@code size} at {@code offset}.
     *
     * @param targetCode byte array of native code
     * @param offset offset at which {@code targetCode} is to be inserted
     * @param size size of {@code targetCode}
     */
    private static void appendBytes(ByteContainer byteContainer, byte[] targetCode, int offset, int size) {
        byteContainer.appendBytes(targetCode, offset, size);
    }

    public void appendCodeBytes(byte[] targetCode, int offset, int size) {
        appendBytes(codeContainer, targetCode, offset, size);
    }

    public void appendIntToCode(int value) {
        codeContainer.appendInt(value);
    }

    public int appendExtLinkageGotBytes(byte[] bytes, int offset, int size) {
        int startOffset = extLinkageGOTContainer.getByteStreamSize();
        appendBytes(extLinkageGOTContainer, bytes, offset, size);
        return startOffset;
    }

    public void addMetadataGotEntry(int offset) {
        metadataGotContainer.appendLong(offset);
    }

    public int addMetaspaceName(String name) {
        Integer value = metaspaceNames.get(name);
        if (value != null) {
            return value.intValue();
        }
        // Get the current length of the stubsNameContainer
        // align on 8-byte boundary
        int nameOffset = alignUp(metaspaceNamesContainer, 8);

        try {
            // Add the name of the symbol to the .stubs.names section
            // Modify them to sequence of utf8 strings with length:
            // "<u2_size>Ljava/lang/ThreadGroup;<u2_size>addUnstarted<u2_size>()V"
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bout);
            int len = name.length();
            if (name.startsWith("Stub")) { // Stub
                out.writeUTF(name);
            } else { // Method or Klass
                int parenthesesIndex = name.lastIndexOf('(', len - 1);
                if (parenthesesIndex > 0) {  // Method name
                    int dotIndex = name.lastIndexOf('.', parenthesesIndex - 1);
                    assert dotIndex > 0 : "method's full name should have '.' : " + name;
                    String klassName = name.substring(0, dotIndex);
                    out.writeUTF(klassName);
                    String methodName = name.substring(dotIndex + 1, parenthesesIndex);
                    out.writeUTF(methodName);
                    String signature = name.substring(parenthesesIndex, len);
                    out.writeUTF(signature);
                } else {
                    out.writeUTF(name); // Klass
                }
            }
            out.writeShort(0); // Terminate by 0.
            byte[] b = bout.toByteArray();
            metaspaceNamesContainer.appendBytes(b, 0, b.length);

            metaspaceNames.put(name, nameOffset);
            return nameOffset;
        } catch (IOException e) {
            throw new InternalError("Failed to append bytes to stubs sections", e);
        }
    }

    /**
     * Add oop symbol by as follows. Extend the oop.got section with another slot for the VM to
     * patch.
     *
     * @param oopName name of the oop symbol
     */
    public Integer addOopSymbol(String oopName) {
        Integer oopGotOffset = getStringOffset(oopName);
        if (oopGotOffset != null) {
            return oopGotOffset;
        }
        return newOopSymbol(oopName);
    }

    private Integer newOopSymbol(String oopName) {
        // Reference to String resolution (ldc).
        int offset = oopGotContainer.getByteStreamSize();
        String gotName = "got.ldc." + offset;
        Symbol relocationSymbol = oopGotContainer.createGotSymbol(gotName);

        if (offset != relocationSymbol.getOffset()) {
            throw new InternalError("offset must equal! (" + offset + " vs " + relocationSymbol.getOffset());
        }

        addStringOffset(oopName, relocationSymbol.getOffset());
        return relocationSymbol.getOffset();
    }

    public int addCountersSymbol(String metaspaceName) {
        String gotName = "got." + metaspaceName;
        Symbol relocationSymbol = getGotSymbol(gotName);
        int metaspaceOffset = -1;
        if (relocationSymbol == null) {
            // Add slots when asked in the .metaspace.got section:
            countersGotContainer.createGotSymbol(gotName);
        }
        return metaspaceOffset;
    }

    public Symbol getGotSymbol(String name) {
        assert name.startsWith("got.");
        return symbolTable.get(name);
    }

    /**
     * Add klass symbol by as follows. - Adding the symbol name to the metaspace.names section - Add
     * the offset of the name in metaspace.names to metaspace.offsets - Extend the klasses.got
     * section with another slot for the VM to patch
     *
     * @param klassName name of the metaspace symbol
     * @return the got offset in the klasses.got of the metaspace symbol
     */
    public int addTwoSlotKlassSymbol(String klassName) {
        String gotName = "got." + klassName;
        Symbol previous = getGotSymbol(gotName);
        assert previous == null : "should be called only once for: " + klassName;
        // Add slots when asked in the .metaspace.got section:
        // First slot
        String gotInitName = "got.init." + klassName;
        GotSymbol slot1Symbol = klassesGotContainer.createGotSymbol(gotInitName);
        GotSymbol slot2Symbol = klassesGotContainer.createGotSymbol(gotName);

        slot1Symbol.getIndex(); // check alignment and ignore result
        // Get the index (offset/8) to the got in the .metaspace.got section
        return slot2Symbol.getIndex();
    }

    public static int addMethodsCount(int count, ReadOnlyDataContainer container) {
        return appendInt(count, container);
    }

    private static int appendInt(int count, ReadOnlyDataContainer container) {
        int offset = container.getByteStreamSize();
        container.appendInt(count);
        return offset;
    }

    /**
     * Add constant data as follows. - Adding the data to the meth.constdata section
     *
     * @param data
     * @param alignment
     * @return the offset in the meth.constdata of the data
     */
    public int addConstantData(byte[] data, int alignment) {
        // Get the current length of the metaspaceNameContainer
        int constantDataOffset = alignUp(constantDataContainer, alignment);
        constantDataContainer.appendBytes(data, 0, data.length);
        alignUp(constantDataContainer, alignment); // Post alignment
        return constantDataOffset;
    }

    public static int alignUp(ByteContainer container, int alignment) {
        if (Integer.bitCount(alignment) != 1) {
            throw new IllegalArgumentException("Must be a power of 2");
        }
        int offset = container.getByteStreamSize();
        int aligned = (offset + (alignment - 1)) & -alignment;
        if (aligned < offset || (aligned & (alignment - 1)) != 0) {
            throw new RuntimeException("Error aligning: " + offset + " -> " + aligned);
        }
        if (aligned != offset) {
            int nullArraySz = aligned - offset;
            byte[] nullArray = new byte[nullArraySz];
            container.appendBytes(nullArray, 0, nullArraySz);
            offset = aligned;
        }
        return offset;
    }

    public void addCodeSegments(int start, int end) {
        assert (start % codeSegmentSize) == 0 : "not aligned code";
        int currentOffset = codeSegmentsContainer.getByteStreamSize();
        int offset = start / codeSegmentSize;
        int emptySize = offset - currentOffset;
        // add empty segments if needed
        if (emptySize > 0) {
            byte[] emptyArray = new byte[emptySize];
            for (int i = 0; i < emptySize; i++) {
                emptyArray[i] = (byte) 0xff;
            }
            appendBytes(codeSegmentsContainer, emptyArray, 0, emptySize);
        }
        int alignedEnd = (end + (codeSegmentSize - 1)) & -codeSegmentSize;
        int segmentsCount = (alignedEnd / codeSegmentSize) - offset;
        byte[] segments = new byte[segmentsCount];
        int idx = 0;
        for (int i = 0; i < segmentsCount; i++) {
            segments[i] = (byte) idx;
            idx = (idx == 0xfe) ? 1 : (idx + 1);
        }
        appendBytes(codeSegmentsContainer, segments, 0, segmentsCount);
    }

    public ByteContainer getExtLinkageGOTContainer() {
        return extLinkageGOTContainer;
    }

    public ReadOnlyDataContainer getMethodMetadataContainer() {
        return methodMetadataContainer;
    }

    public ReadOnlyDataContainer getMetaspaceNamesContainer() {
        return metaspaceNamesContainer;
    }

    public ReadOnlyDataContainer getMethodsOffsetsContainer() {
        return methodsOffsetsContainer;
    }

    public ReadOnlyDataContainer getKlassesOffsetsContainer() {
        return klassesOffsetsContainer;
    }

    public ReadOnlyDataContainer getKlassesDependenciesContainer() {
        return klassesDependenciesContainer;
    }

    public ReadOnlyDataContainer getStubsOffsetsContainer() {
        return stubsOffsetsContainer;
    }

    public ReadOnlyDataContainer getCodeSegmentsContainer() {
        return codeSegmentsContainer;
    }

    public ReadOnlyDataContainer getConstantDataContainer() {
        return constantDataContainer;
    }

    public ByteContainer getKlassesGotContainer() {
        return klassesGotContainer;
    }

    public ByteContainer getCountersGotContainer() {
        return countersGotContainer;
    }

    public ByteContainer getMetadataGotContainer() {
        return metadataGotContainer;
    }

    public ByteContainer getMethodStateContainer() {
        return methodStateContainer;
    }

    public ByteContainer getOopGotContainer() {
        return oopGotContainer;
    }

    public CodeContainer getCodeContainer() {
        return codeContainer;
    }

    public ReadOnlyDataContainer getConfigContainer() {
        return configContainer;
    }

    public Map<Symbol, Relocation> getUniqueRelocationTable() {
        return uniqueRelocationTable;
    }

    public HeaderContainer getHeaderContainer() {
        return headerContainer;
    }

}
