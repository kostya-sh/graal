@0x9eb32e19f86ee174;
using Java = import "/capnp/java.capnp";
$Java.package("com.oracle.graal.pointsto.heap");
$Java.outerClassname("SharedLayerSnapshotCapnProtoSchemaHolder");

using TypeId = Int32;
using MethodId = Int32;
using FieldId = Int32;
using ConstantId = Int32;
using SingletonObjId = Int32;

struct PersistedAnalysisType {
  id @0 :TypeId;
  descriptor @1 :Text;
  fields @2 :List(FieldId);
  hubIdentityHashCode @3 :Int32;
  classJavaName @4 :Text;
  className @5 :Text;
  modifiers @6 :Int32;
  # Most of these fields apply only to instances and could be in a union or a separate structure:
  isInterface @7 :Bool;
  isEnum @8 :Bool;
  isInitialized @9 :Bool;
  isInitializedAtBuildTime @10 :Bool;
  isLinked @11 :Bool;
  sourceFileName @12 :Text;
  enclosingTypeId @13 :TypeId;
  componentTypeId @14 :TypeId;
  superClassTypeId @15 :TypeId;
  isInstantiated @16 :Bool;
  isUnsafeAllocated @17 :Bool;
  isReachable @18 :Bool;
  interfaces @19 :List(TypeId);
  instanceFieldIds @20 :List(FieldId);
  instanceFieldIdsWithSuper @21 :List(FieldId);
  staticFieldIds @22 :List(FieldId);
  annotationList @23 :List(Annotation);
  classInitializationInfo @24 :ClassInitializationInfo;
  wrappedType :union {
    none @25 :Void; # default
    serializationGenerated :group {
      rawDeclaringClass @26 :Text;
      rawTargetConstructor @27 :Text;
    }
    lambda :group {
      capturingClass @28 :Text;
    }
    proxyType @29 :Void;
  }
}

struct ClassInitializationInfo {
  isNoInitializerNoTracking @0 :Bool;
  isInitializedNoTracking @1 :Bool;
  isFailedNoTracking @2 :Bool;
  isInitialized @3 :Bool;
  isInErrorState @4 :Bool;
  isLinked @5 :Bool;
  hasInitializer @6 :Bool;
  isBuildTimeInitialized @7 :Bool;
  isTracked @8 :Bool;
  initializerMethodId @9 :MethodId;
}

struct PersistedAnalysisMethod {
  id @0 :MethodId;
  descriptor @1 :Text;
  name @2 :Text;
  className @3 :Text;
  declaringTypeId @4 :TypeId;
  argumentClassNames @5 :List(Text);
  argumentTypeIds @6 :List(TypeId);
  returnTypeId @7 :TypeId;
  modifiers @8 :Int32;
  code @9 :Data;
  codeSize @10 :Int32;
  isConstructor @11 :Bool;
  isSynthetic @12 :Bool;
  canBeStaticallyBound @13 :Bool;
  isVirtualRootMethod @14 :Bool;
  isDirectRootMethod @15 :Bool;
  isInvoked @16 :Bool;
  isImplementationInvoked @17 :Bool;
  isIntrinsicMethod @18 :Bool;
  methodHandleIntrinsicName @19 :Text;
  annotationList @20 :List(Annotation);
  isVarArgs @21 :Bool;
  analysisGraphLocation @22 :Text;
  analysisGraphIsIntrinsic @23 :Bool;
  strengthenedGraphLocation @24 :Text;
  wrappedMethod :union {
    none @25 :Void; # default
    factoryMethod :group {
      targetConstructorId @26 :MethodId;
      throwAllocatedObject @27 :Bool;
      instantiatedTypeId @28 :TypeId;
    }
    outlinedSB :group {
      methodTypeReturn @29 :Text;
      methodTypeParameters @30 :List(Text);
    }
    cEntryPointCallStub :group {
      originalMethodId @31 :MethodId;
      notPublished @32 :Bool;
    }
    wrappedMember :group {
      union {
        reflectionExpandSignature @33 :Void;
        javaCallVariantWrapper @34 :Void;
      }
      name @35 :Text;
      declaringClassName @36 :Text;
      argumentTypeNames @37 :List(Text);
    }
  }
}

struct PersistedAnalysisField {
  id @0 :FieldId;
  className @1 :Text;
  declaringTypeId @2 :TypeId;
  typeId @3 :TypeId;
  position @4 :Int32;
  location @5 :Int32;
  modifiers @6 :Int32;
  isInternal @7 :Bool;
  isAccessed @8 :Bool;
  isRead @9 :Bool;
  isWritten @10 :Bool;
  isFolded @11 :Bool;
  isStatic @12 :Bool;
  isSynthetic @13 :Bool;
  annotationList @14 :List(Annotation);
  name @15 :Text;
  fieldCheckIndex @16 :Int32;
}

struct CEntryPointLiteralReference {
  methodName @0 :Text;
  definingClass @1 :Text;
  parameterNames @2 :List(Text);
}

struct ConstantReference {
  union {
    objectConstant :group {
      constantId @0 :ConstantId;
    }
    nullPointer @1 :Void;
    notMaterialized @2 :Void;
    primitiveValue :group {
      typeChar @3 :Int8;
      rawValue @4 :Int64;

    }
    methodPointer :group {
      methodId @5 :MethodId;
    }
    cEntryPointLiteralCodePointer @6 :CEntryPointLiteralReference;
  }
}

struct PersistedConstant {
  id @0 :ConstantId;
  typeId @1 :TypeId;
  identityHashCode @2 :Int32;
  isSimulated @3 :Bool;
  objectOffset @4 :Int64;
  union {
    object :group {
      data @5 :List(ConstantReference);
      union {
        instance @6 :Void;
        objectArray @7 :Void;
      }
      relinking :union {
        notRelinked @8 :Void; # default
        stringConstant :group {
          value @9 :Text;
        }
        enumConstant :group {
          enumClass @10 :Text;
          enumName @11 :Text;
        }
        classConstant :group {
          typeId @12 :TypeId;
        }
      }
    }
    primitiveData :union {
      z @13 :List(Bool);
      b @14 :List(Int8);
      s @15 :List(Int16);
      c @16 :List(UInt16);
      i @17 :List(Int32);
      f @18 :List(Float32);
      j @19 :List(Int64);
      d @20 :List(Float64);
    }
    relocatable :group {
      key @21 :Text;
    }
  }
  parentConstantId @22 :ConstantId;
  parentIndex @23 :Int32;
}

struct KeyStoreEntry {
  key @0 :Text;
  value :union {
    i @1 :Int32;
    il @2 :List(Int32);
    j @3 :Int64;
    str @4 :Text;
    strl @5 :List(Text);
    zl @6 :List(Bool);
  }
}

struct ImageSingletonKey {
  keyClassName @0 :Text;
  persistFlag @1 :Int32;
  objectId @2 :SingletonObjId;
}

struct ImageSingletonObject {
  id @0 :SingletonObjId;
  className @1 :Text;
  store @2 :List(KeyStoreEntry);
}

struct Annotation {
  typeName @0 :Text;
  values @1 :List(AnnotationValue);
}

struct AnnotationValue {
  name @0 :Text;
  union {
    other @1 :Text;
    enum :group {
      className @2 :Text;
      name @3 :Text;
    }
  }
}

struct SharedLayerSnapshot {
  nextTypeId @0 :TypeId;
  nextMethodId @1 :MethodId;
  nextFieldId @2 :FieldId;
  nextConstantId @3 :ConstantId;
  staticPrimitiveFieldsConstantId @4 :ConstantId;
  staticObjectFieldsConstantId @5 :ConstantId;
  imageHeapSize @6 :Int64;
  constantsToRelink @7 :List(ConstantId);
  types @8 :List(PersistedAnalysisType);
  methods @9 :List(PersistedAnalysisMethod);
  constants @10 :List(PersistedConstant);
  singletonKeys @11 :List(ImageSingletonKey);
  singletonObjects @12 :List(ImageSingletonObject);
  fields @13 :List(PersistedAnalysisField);
}
