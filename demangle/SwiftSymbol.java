/*
 * Created by Yin Congxiao.
 */

package demangle;

import java.util.Collections;
import java.util.List;

public class SwiftSymbol {

    public enum FunctionSigSpecializationParamKind {
        constantPropFunction(0),
        constantPropGlobal(1),
        constantPropInteger(2),
        constantPropFloat(3),
        constantPropString(4),
        closureProp(5),
        boxToValue(6),
        boxToStack(7),

        dead(64),
        ownedToGuaranteed(128),
        sroa(256),
        guaranteedToOwned(512),
        existentialToGeneric(1024);

        private int value;

        private FunctionSigSpecializationParamKind(int i) {
            this.value = i;
        }

        public int getValue() {
            return this.value;
        }

        public static FunctionSigSpecializationParamKind paramKind(int nCode) {
            FunctionSigSpecializationParamKind[] enums = FunctionSigSpecializationParamKind.values();
            for (FunctionSigSpecializationParamKind em : enums) {
                if (em.value == nCode) {
                    return em;
                }
            }
            return null;
        }

        public String description() {
            switch (this) {
                case boxToValue: return "Value Promoted from Box";
                case boxToStack: return "Stack Promoted from Box";
                case constantPropFunction: return "Constant Propagated Function";
                case constantPropGlobal: return "Constant Propagated Global";
                case constantPropInteger: return "Constant Propagated Integer";
                case constantPropFloat: return "Constant Propagated Float";
                case constantPropString: return "Constant Propagated String";
                case closureProp: return "Closure Propagated";
                case existentialToGeneric: return "Existential To Protocol Constrained Generic";
                case dead: return "Dead";
                case ownedToGuaranteed: return "Owned To Guaranteed";
                case guaranteedToOwned: return "Guaranteed To Owned";
                case sroa: return "Exploded";
            }
            return "";
        }
    }

    public enum SpecializationPass {
        allocBoxToStack,
        closureSpecializer,
        capturePromotion,
        capturePropagation,
        functionSignatureOpts,
        genericSpecializer
    }

    public enum Directness {
        direct(0),
        indirect(1);
        public int nCode;
        private Directness(int n) {
            this.nCode = n;
        }

        static public Directness DirectnessWithValue(int n) {
            for (Directness dir : Directness.values()) {
                if (dir.nCode == n) {
                    return dir;
                }
            }
            return null;
        }

        public String description() {
            switch (this) {
                case indirect:return "indirect";
                case direct:return "direct";
            }
            return "";
        }
    }

    public enum DemangleFunctionEntityArgs {
        none,
        typeAndMaybePrivateName,
        typeAndIndex,
        index
    }

    public enum DemangleGenericRequirementTypeKind {
        generic,
        assoc,
        compoundAssoc,
        substitution
    }

    public enum DemangleGenericRequirementConstraintKind {
        protocol,
        baseClass,
        sameType,
        layout
    }

    public class ConstraintAndTypeKinds {
        public DemangleGenericRequirementConstraintKind constraint;
        public DemangleGenericRequirementTypeKind type;

        void setConstraintAndType(DemangleGenericRequirementConstraintKind constraint, DemangleGenericRequirementTypeKind type) {
            this.constraint = constraint;
            this.type = type;
        }
    }

    public enum ValueWitnessKind {
        allocateBuffer(0),
        assignWithCopy(1),
        assignWithTake(2),
        deallocateBuffer(3),
        destroy(4),
        destroyArray(5),
        destroyBuffer(6),
        initializeBufferWithCopyOfBuffer(7),
        initializeBufferWithCopy(8),
        initializeWithCopy(9),
        initializeBufferWithTake(10),
        initializeWithTake(11),
        projectBuffer(12),
        initializeBufferWithTakeOfBuffer(13),
        initializeArrayWithCopy(14),
        initializeArrayWithTakeFrontToBack(15),
        initializeArrayWithTakeBackToFront(16),
        storeExtraInhabitant(17),
        getExtraInhabitantIndex(18),
        getEnumTag(19),
        destructiveProjectEnumData(20),
        destructiveInjectEnumTag(21),
        getEnumTagSinglePayload(22),
        storeEnumTagSinglePayload(23);

        public int nCode;

        private ValueWitnessKind(int n) {
            this.nCode = n;
        }

        public static ValueWitnessKind WitnessKindWithString(int nCode) {
            for (ValueWitnessKind dir : ValueWitnessKind.values()) {
                if (dir.nCode == nCode) {
                    return dir;
                }
            }
            return null;
        }

        public static ValueWitnessKind WitnessKindWithString(String str) {
            switch (str) {
                case "al":
                    return ValueWitnessKind.allocateBuffer;
                case "ca":
                    return ValueWitnessKind.assignWithCopy;
                case "ta":
                    return ValueWitnessKind.assignWithTake;
                case "de":
                    return ValueWitnessKind.deallocateBuffer;
                case "xx":
                    return ValueWitnessKind.destroy;
                case "XX":
                    return ValueWitnessKind.destroyBuffer;
                case "Xx":
                    return ValueWitnessKind.destroyArray;
                case "CP":
                    return ValueWitnessKind.initializeBufferWithCopyOfBuffer;
                case "Cp":
                    return ValueWitnessKind.initializeBufferWithCopy;
                case "cp":
                    return ValueWitnessKind.initializeWithCopy;
                case "Tk":
                    return ValueWitnessKind.initializeBufferWithTake;
                case "tk":
                    return ValueWitnessKind.initializeWithTake;
                case "pr":
                    return ValueWitnessKind.projectBuffer;
                case "TK":
                    return ValueWitnessKind.initializeBufferWithTakeOfBuffer;
                case "Cc":
                    return ValueWitnessKind.initializeArrayWithCopy;
                case "Tt":
                    return ValueWitnessKind.initializeArrayWithTakeFrontToBack;
                case "tT":
                    return ValueWitnessKind.initializeArrayWithTakeBackToFront;
                case "xs":
                    return ValueWitnessKind.storeExtraInhabitant;
                case "xg":
                    return ValueWitnessKind.getExtraInhabitantIndex;
                case "ug":
                    return ValueWitnessKind.getEnumTag;
                case "up":
                    return ValueWitnessKind.destructiveProjectEnumData;
                case "ui":
                    return ValueWitnessKind.destructiveInjectEnumTag;
                case "et":
                    return ValueWitnessKind.getEnumTagSinglePayload;
                case "st":
                    return ValueWitnessKind.storeEnumTagSinglePayload;
                default:
                    return null;
            }
        }

        public String toString() {
            switch (this) {
                case allocateBuffer:
                    return "allocateBuffer";
                case assignWithCopy:
                    return "assignWithCopy";
                case assignWithTake:
                    return "assignWithTake";
                case deallocateBuffer:
                    return "deallocateBuffer";
                case destroy:
                    return "destroy";
                case destroyBuffer:
                    return "destroyBuffer";
                case initializeBufferWithCopyOfBuffer:
                    return "initializeBufferWithCopyOfBuffer";
                case initializeBufferWithCopy:
                    return "initializeBufferWithCopy";
                case initializeWithCopy:
                    return "initializeWithCopy";
                case initializeBufferWithTake:
                    return "initializeBufferWithTake";
                case initializeWithTake:
                    return "initializeWithTake";
                case projectBuffer:
                    return "projectBuffer";
                case initializeBufferWithTakeOfBuffer:
                    return "initializeBufferWithTakeOfBuffer";
                case destroyArray:
                    return "destroyArray";
                case initializeArrayWithCopy:
                    return "initializeArrayWithCopy";
                case initializeArrayWithTakeFrontToBack:
                    return "initializeArrayWithTakeFrontToBack";
                case initializeArrayWithTakeBackToFront:
                    return "initializeArrayWithTakeBackToFront";
                case storeExtraInhabitant:
                    return "storeExtraInhabitant";
                case getExtraInhabitantIndex:
                    return "getExtraInhabitantIndex";
                case getEnumTag:
                    return "getEnumTag";
                case destructiveProjectEnumData:
                    return "destructiveProjectEnumData";
                case destructiveInjectEnumTag:
                    return "destructiveInjectEnumTag";
                case getEnumTagSinglePayload:
                    return "getEnumTagSinglePayload";
                case storeEnumTagSinglePayload:
                    return "storeEnumTagSinglePayload";
            }
            return "";
        }
    }

    public enum Kind {
        allocator,
        anonymousContext,
        anonymousDescriptor,
        argumentTuple,
        associatedConformanceDescriptor,
        associatedType,
        associatedTypeDescriptor,
        associatedTypeGenericParamRef,
        associatedTypeMetadataAccessor,
        associatedTypeRef,
        associatedTypeWitnessTableAccessor,
        assocTypePath,
        autoClosureType,
        boundGenericClass,
        boundGenericEnum,
        boundGenericFunction,
        boundGenericOtherNominalType,
        boundGenericProtocol,
        boundGenericStructure,
        boundGenericTypeAlias,
        builtinTypeName,
        cFunctionPointer,
        theClass,
        classMetadataBaseOffset,
        constructor,
        coroutineContinuationPrototype,
        curryThunk,
        deallocator,
        declContext,
        defaultArgumentInitializer,
        defaultAssociatedConformanceAccessor,
        defaultAssociatedTypeMetadataAccessor,
        dependentAssociatedTypeRef,
        dependentGenericConformanceRequirement,
        dependentGenericLayoutRequirement,
        dependentGenericParamCount,
        dependentGenericParamType,
        dependentGenericSameTypeRequirement,
        dependentGenericSignature,
        dependentGenericType,
        dependentMemberType,
        dependentPseudogenericSignature,
        destructor,
        didSet,
        directMethodReferenceAttribute,
        directness,
        dispatchThunk,
        dynamicAttribute,
        dynamicSelf,
        emptyList,
        theEnum,
        enumCase,
        errorType,
        escapingAutoClosureType,
        existentialMetatype,
        explicitClosure,
        extension,
        extensionDescriptor,
        fieldOffset,
        firstElementMarker,
        fullTypeMetadata,
        function,
        functionSignatureSpecialization,
        functionSignatureSpecializationParam,
        functionSignatureSpecializationParamKind,
        functionSignatureSpecializationParamPayload,
        functionType,
        genericPartialSpecialization,
        genericPartialSpecializationNotReAbstracted,
        genericProtocolWitnessTable,
        genericProtocolWitnessTableInstantiationFunction,
        genericSpecialization,
        genericSpecializationNotReAbstracted,
        genericSpecializationParam,
        genericTypeMetadataPattern,
        genericTypeParamDecl,
        getter,
        global,
        globalGetter,
        identifier,
        implConvention,
        implErrorResult,
        implEscaping,
        implFunctionAttribute,
        implFunctionType,
        implicitClosure,
        implParameter,
        implResult,
        index,
        infixOperator,
        initializer,
        inOut,
        iVarDestroyer,
        iVarInitializer,
        keyPathEqualsThunkHelper,
        keyPathGetterThunkHelper,
        keyPathHashThunkHelper,
        keyPathSetterThunkHelper,
        labelList,
        lazyProtocolWitnessTableAccessor,
        lazyProtocolWitnessTableCacheVariable,
        localDeclName,
        materializeForSet,
        mergedFunction,
        metaclass,
        metatype,
        metatypeRepresentation,
        methodDescriptor,
        methodLookupFunction,
        modifyAccessor,
        module,
        moduleDescriptor,
        nativeOwningAddressor,
        nativeOwningMutableAddressor,
        nativePinningAddressor, nativePinningMutableAddressor,
        noEscapeFunctionType,
        nominalTypeDescriptor,
        nonObjCAttribute,
        number,
        objCAttribute,
        objCBlock,
        otherNominalType,
        outlinedAssignWithCopy,
        outlinedAssignWithTake,
        outlinedBridgedMethod,
        outlinedConsume,
        outlinedCopy,
        outlinedDestroy,
        outlinedInitializeWithCopy,
        outlinedInitializeWithTake,
        outlinedRelease,
        outlinedRetain,
        outlinedVariable,
        owned,
        owningAddressor,
        owningMutableAddressor,
        partialApplyForwarder,
        partialApplyObjCForwarder,
        postfixOperator,
        prefixOperator,
        privateDeclName,
        propertyDescriptor,
        protocol,
        protocolConformance,
        protocolConformanceDescriptor,
        protocolDescriptor,
        protocolList,
        protocolListWithAnyObject,
        protocolListWithClass,
        protocolRequirementsBaseDescriptor,
        protocolWitness,
        protocolWitnessTable,
        protocolWitnessTableAccessor,
        protocolWitnessTablePattern,
        reabstractionThunk,
        reabstractionThunkHelper,
        readAccessor,
        reflectionMetadataAssocTypeDescriptor,
        reflectionMetadataBuiltinDescriptor,
        reflectionMetadataFieldDescriptor,
        reflectionMetadataSuperclassDescriptor,
        relatedEntityDeclName,
        resilientProtocolWitnessTable,
        retroactiveConformance,
        returnType,
        setter,
        shared,
        silBoxImmutableField,
        silBoxLayout,
        silBoxMutableField,
        silBoxType,
        silBoxTypeWithLayout,
        specializationIsFragile,
        specializationPassID,
        theStatic,
        structure,
        subscript,
        suffix,
        symbolicReference,
        thinFunctionType,
        throwsAnnotation,
        tuple,
        tupleElement,
        tupleElementName,
        type,
        typeAlias,
        typeList,
        typeMangling,
        typeMetadata,
        typeMetadataAccessFunction,
        typeMetadataCompletionFunction,
        typeMetadataInstantiationCache,
        typeMetadataInstantiationFunction,
        typeMetadataLazyCache,
        typeMetadataSingletonInitializationCache,
        uncurriedFunctionType,
        unmanaged,
        unowned,
        unresolvedSymbolicReference,
        unsafeAddressor,
        unsafeMutableAddressor,
        valueWitness,
        valueWitnessTable,
        variable,
        variadicMarker,
        vTableAttribute,
        vTableThunk,
        weak,
        willSet;

        public boolean isDeclName() {
            boolean isDecl = false;
            switch (this) {
                case identifier:
                case localDeclName:
                case privateDeclName:
                case relatedEntityDeclName:
                case prefixOperator:
                case postfixOperator:
                case infixOperator:
                case unresolvedSymbolicReference:
                case symbolicReference:
                    isDecl = true;
            }
            return isDecl;
        }

        public boolean isContext() {
            boolean isContext = false;
            switch (this) {
                case allocator:
                case theClass:
                case anonymousContext:
                case constructor:
                case curryThunk:
                case deallocator:
                case defaultArgumentInitializer:
                case destructor:
                case didSet:
                case dispatchThunk:
                case theEnum:
                case explicitClosure:
                case extension:
                case function:
                case getter:
                case globalGetter:
                case iVarInitializer:
                case iVarDestroyer:
                case implicitClosure:
                case initializer:
                case materializeForSet:
                case module:
                case nativeOwningAddressor:
                case nativeOwningMutableAddressor:
                case nativePinningAddressor:
                case nativePinningMutableAddressor:
                case otherNominalType:
                case owningAddressor:
                case owningMutableAddressor:
                case protocol:
                case setter:
                case theStatic:
                case structure:
                case subscript:
                case symbolicReference:
                case typeAlias:
                case unresolvedSymbolicReference:
                case unsafeAddressor:
                case unsafeMutableAddressor:
                case variable:
                case willSet:
                    isContext = true;
            }
            return isContext;
        }

        public boolean isAnyGeneric() {
            boolean isAnyGeneric = false;
            switch (this) {
                case structure:
                case theClass:
                case theEnum:
                case protocol:
                case otherNominalType:
                case typeAlias:
                case symbolicReference:
                case unresolvedSymbolicReference:
                    isAnyGeneric = true;
            }
            return isAnyGeneric;
        }

        public boolean isEntity() {
            return this == Kind.type || isContext();
        }

        public boolean isRequrement() {
            boolean isRequrement = false;
            switch (this) {
                case dependentGenericSameTypeRequirement:
                case dependentGenericLayoutRequirement:
                case dependentGenericConformanceRequirement:
                    isRequrement = true;
            }
            return isRequrement;
        }

        public boolean isFunctionAttr() {
            boolean isFunctionAttr = false;
            switch (this) {
                case functionSignatureSpecialization:
                case genericPartialSpecialization:
                case genericSpecializationNotReAbstracted:
                case genericSpecialization:
                case genericPartialSpecializationNotReAbstracted:
                case objCAttribute:
                case nonObjCAttribute:
                case dynamicAttribute:
                case directMethodReferenceAttribute:
                case vTableAttribute:
                case partialApplyForwarder:
                case partialApplyObjCForwarder:
                case outlinedVariable:
                case outlinedBridgedMethod:
                case mergedFunction:
                    isFunctionAttr = true;
            }
            return isFunctionAttr;
        }

        public boolean isExistentialType() {
            switch (this) {
                case existentialMetatype:
                case protocolList:
                case protocolListWithClass:
                case protocolListWithAnyObject:
                    return true;
                default:
                    return false;
            }
        }

        public boolean isSimpleType() {
            switch (this) {
                case associatedType:
                case associatedTypeRef:
                case boundGenericClass:
                case boundGenericEnum:
                case boundGenericStructure:
                case boundGenericOtherNominalType:
                case builtinTypeName:
                case theClass:
                case dependentGenericType:
                case dependentMemberType:
                case dependentGenericParamType:
                case dynamicSelf:
                case theEnum:
                case errorType:
                case existentialMetatype:
                case metatype:
                case metatypeRepresentation:
                case module:
                case tuple:
                case protocol:
                case returnType:
                case silBoxType:
                case silBoxTypeWithLayout:
                case structure:
                case otherNominalType:
                case tupleElementName:
                case type:
                case typeAlias:
                case typeList:
                case labelList:
                case symbolicReference:
                case unresolvedSymbolicReference:
                    return true;
                default:
                    return false;
            }
        }

    }

    public Kind kind;
    public List<SwiftSymbol> children;
    public Contents contents;
    public String target;

    public SwiftSymbol() {
        this.kind = null;
        this.children = new SafeArrayList<>();
        this.contents = new Contents();
    }

    public SwiftSymbol(Kind kind, List<SwiftSymbol> children, Contents contents) {
        if (children == null) children = new SafeArrayList<>();
        if (contents == null) contents = new Contents();
        this.kind = kind;
        this.children = children;
        this.contents = contents;
    }

    public SwiftSymbol(Kind kind, SwiftSymbol child) {
        this(kind, Utility.CreateLists(child), new Contents());
    }

    public static SwiftSymbol SwiftSymbolWithChild(Kind kind, SwiftSymbol childChild) {
        return new SwiftSymbol(Kind.type, Utility.CreateLists(new SwiftSymbol(kind, Utility.CreateLists(childChild), null)), new Contents());
    }

    public SwiftSymbol(Kind typeWithChildKind, List<SwiftSymbol> childChildren) {
        this.kind = Kind.type;
        this.children = Utility.CreateLists(new SwiftSymbol(typeWithChildKind, childChildren, new Contents()));
        this.contents = new Contents();
    }

    public SwiftSymbol(Kind swiftStdlibTypeKind, String name) {
        SwiftSymbol child1 = new SwiftSymbol(Kind.module, new SafeArrayList<>(), new Contents(Demangler.stdlibName));
        SwiftSymbol child2 = new SwiftSymbol(Kind.identifier, new SafeArrayList<>(), new Contents(name));
        SafeArrayList children1 = Utility.CreateLists(child1, child2);
        SafeArrayList children2 = Utility.CreateLists(new SwiftSymbol(swiftStdlibTypeKind, children1, null));
        this.kind = Kind.type;
        this.children = children2;
        this.contents = new Contents();
    }

    public static SwiftSymbol SwiftSymbolWithBuiltinType(Kind swiftBuiltinType, String name) {
        return new SwiftSymbol(Kind.type, Utility.CreateLists(new SwiftSymbol(swiftBuiltinType, Utility.CreateLists(), new Contents(name))), null);
    }

    public SwiftSymbol changeChild(SwiftSymbol newChild, int atIndex) {
        if (atIndex < 0 || atIndex >= children.size()) return this;

        List modifiedChildren = children;
        if (newChild != null) {
            modifiedChildren.set(atIndex, newChild);
        } else {
            modifiedChildren.remove(atIndex);
        }
        return new SwiftSymbol(kind, modifiedChildren, contents);
    }

    public SwiftSymbol changeKind(Kind newKind, List additionalChildren) {
        children.addAll(additionalChildren);
        return new SwiftSymbol(newKind, children, contents);
    }

    public String text() {
        return contents.getName();
    }

    public int index() {
        return contents.getIndex();
    }

    public boolean needSpaceBeforeType() {
        switch (this.kind) {
            case type: {
                SwiftSymbol swiftSymbol = children.get(0);
                if (swiftSymbol != null) return swiftSymbol.needSpaceBeforeType();
                return false;
            }
            case functionType:
            case noEscapeFunctionType:
            case uncurriedFunctionType:
            case dependentGenericType:
                return false;
            default:
                return true;
        }
    }

    public boolean isIdentifier(String desired) {
        return kind == Kind.identifier && text().equals(desired);
    }

    public boolean isDeclName() {
        switch (kind) {
            case identifier:
            case localDeclName:
            case argumentTuple:
        }
        return false;

    }

    public boolean isSwiftModule (){
        return kind == Kind.module && text() == Demangler.stdlibName;
    }

    //output
    public String print(@SymbolPrinter.SymbolPrintOptions int options) {
        SymbolPrinter printer = new SymbolPrinter(options);
        printer.printName(this, false);
        return printer.target;
    }

    public String description() {
        SymbolPrinter printer = new SymbolPrinter(SymbolPrinter.defaultOptions);
        printer.printName(this, false);
        return printer.target;
    }

}
