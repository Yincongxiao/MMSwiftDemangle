/*
 * Created by Yin Congxiao.
 */


package demangle;

import java.util.*;

public class Demangler {

    @FunctionalInterface
    public interface DemanglerCallback {
        boolean dealKind(SwiftSymbol.Kind kind);
    }

    //const
    static String stdlibName = "Swift";
    private static String objcModule = "__C";
    private static String cModule = "__C_Synthesized";
    public static String lldbExpressionsModuleNamePrefix = "__lldb_expr_";
    private static int maxRepeatCount = 2048;
    private static int maxNumWords = 26;

    private SymbolScanner scanner;
    private Stack<SwiftSymbol> nameStack;
    private List<SwiftSymbol> substitutions;
    private List<String> words;
    private boolean isOldFunctionTypeMangling;

    public Demangler(String mangled) throws Exception {
        this.scanner = new SymbolScanner(mangled);
        reset();
    }

    private void reset() {
        this.nameStack = new Stack<>();
        this.substitutions = new SafeArrayList<>();
        this.words = new SafeArrayList<>();
        this.scanner.reset();
    }

    public SwiftSymbol demangleSymbol() throws Exception {
        if (scanner.conditional("_Tt")) {
            return demangleObjCTypeName();
        } else if (scanner.conditional("_T")) {
            isOldFunctionTypeMangling = true;
            scanner.backtrack(2);
        }

        readManglingPrefix();
        parseAndPushNames();
        SwiftSymbol topLevel = new SwiftSymbol(SwiftSymbol.Kind.global, new ArrayList<>(), new Contents());
        popTopLevelInto(topLevel);
        return topLevel;
    }

    public void readManglingPrefix() throws Exception {
        char char0 = scanner.readChar();
        char char1 = scanner.readChar();
        if (char0 == '_' && char1 == 'T') {
            scanner.match("0");
        } else if (char0 == '_' && char1 == '$' && (scanner.conditional("S") || scanner.conditional("s"))) {
            return;
        } else if (char0 == '$' && (char1 == 'S' || char1 == 's')) {
            return;
        } else {
            throw new Exception("<readManglingPrefix()>: prefix is not validate!");
        }
    }

    private void parseAndPushNames() throws Exception {
        while (!scanner.isAtEnd()) {
            nameStack.add(demangleOperator());
        }
    }

    private SwiftSymbol demangleOperator() throws Exception {
        char c = scanner.readChar();
        switch (c) {
            case 0xFF:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
            case 6:
            case 7:
            case 8:
            case 9:
            case 0xA:
            case 0xB:
            case 0xC: {
                return new SwiftSymbol(SwiftSymbol.Kind.unresolvedSymbolicReference, new SafeArrayList<>(), new Contents());
            }
            case 'A':
                return demangleMultiSubstitutions();
            case 'B':
                return demangleBuiltinType();
            case 'C':
                return demangleAnyGenericType(SwiftSymbol.Kind.theClass);
            case 'D':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMangling, require(pop(SwiftSymbol.Kind.type)));
            case 'E':
                return demangleExtensionContext();
            case 'F':
                return demanglePlainFunction();
            case 'G':
                return demangleBoundGenericType();
            case 'I':
                return demangleImplFunctionType();
            case 'K':
                return new SwiftSymbol(SwiftSymbol.Kind.throwsAnnotation, (SwiftSymbol) null);
            case 'L':
                return demangleLocalIdentifier();
            case 'M':
                return demangleMetatype();
            case 'N':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadata, require(pop(SwiftSymbol.Kind.type)));
            case 'O':
                return demangleAnyGenericType(SwiftSymbol.Kind.theEnum);
            case 'P':
                return demangleAnyGenericType(SwiftSymbol.Kind.protocol);
            case 'Q':
                return demangleArchetype();
            case 'R':
                return demangleGenericRequirement();
            case 'S':
                return demangleStandardSubstitution();
            case 'T':
                return demangleThunkOrSpecialization();
            case 'V':
                return demangleAnyGenericType(SwiftSymbol.Kind.structure);
            case 'W':
                return demangleWitness();
            case 'X':
                return demangleSpecialType();
            case 'Z':
                return new SwiftSymbol(SwiftSymbol.Kind.theStatic, require(pop(kind -> kind.isEntity())));
            case 'a':
                return demangleAnyGenericType(SwiftSymbol.Kind.typeAlias);
            case 'c':
                return require(popFunctionType(SwiftSymbol.Kind.functionType));
            case 'd':
                return new SwiftSymbol(SwiftSymbol.Kind.variadicMarker, (SwiftSymbol) null);
            case 'f':
                return demangleFunctionEntity();
            case 'g':
                return demangleRetroactiveConformance();
            case 'h':
                return SwiftSymbol.SwiftSymbolWithChild(SwiftSymbol.Kind.shared, require(popTypeAndGetChild()));
            case 'i':
                return demangleSubscript();
            case 'l':
                return demangleGenericSignature(false);
            case 'm':
                return SwiftSymbol.SwiftSymbolWithChild(SwiftSymbol.Kind.type, require(pop(SwiftSymbol.Kind.type)));
            case 'n':
                return new SwiftSymbol(SwiftSymbol.Kind.owned, popTypeAndGetChild());
            case 'o':
                return demangleOperatorIdentifier();
            case 'p':
                return demangleProtocolListType();
            case 'q':
                return new SwiftSymbol(SwiftSymbol.Kind.type, demangleGenericParamIndex());
            case 'r':
                return demangleGenericSignature(true);
            case 's':
                return new SwiftSymbol(SwiftSymbol.Kind.module, new SafeArrayList(), new Contents(stdlibName));
            case 't':
                return popTuple();
            case 'u':
                return demangleGenericType();
            case 'v':
                return demangleVariable();
            case 'w':
                return demangleValueWitness();
            case 'x':
                return new SwiftSymbol(SwiftSymbol.Kind.type, getDependentGenericParamType(0, 0));
            case 'y':
                return new SwiftSymbol(SwiftSymbol.Kind.emptyList, (SwiftSymbol) null);
            case 'z':
                return SwiftSymbol.SwiftSymbolWithChild(SwiftSymbol.Kind.inOut, require(popTypeAndGetChild()));
            case '_':
                return new SwiftSymbol(SwiftSymbol.Kind.firstElementMarker, (SwiftSymbol) null);
            case '.':
                scanner.backtrack(1);
                return new SwiftSymbol(SwiftSymbol.Kind.suffix, new SafeArrayList(), new Contents(scanner.remainder()));
            default:
                scanner.backtrack(1);
                return demangleIdentifier();
        }
    }

    private void popTopLevelInto(SwiftSymbol parent) throws Exception {
        while (true) {
            SwiftSymbol funcAttr = pop(kind -> kind.isFunctionAttr());
            if (funcAttr == null) break;
            switch (funcAttr.kind) {
                case partialApplyForwarder:
                case partialApplyObjCForwarder: {
                    popTopLevelInto(funcAttr);
                    parent.children.add(funcAttr);
                    return;
                }
                default:
                    parent.children.add(funcAttr);
            }
        }
        for (SwiftSymbol name : nameStack) {
            switch (name.kind) {
                case type:
                    parent.children.add(require(name.children.get(0)));
                    break;
                default:
                    parent.children.add(name);
            }
        }

        require(parent.children.size() != 0);
    }

    private int demangleNatural() throws Exception {
        return scanner.conditionalInt();
    }

    private int demangleIndex() throws Exception {
        if (scanner.conditional("_")) {
            return 0;
        }
        int value = require(demangleNatural());
        scanner.match("_");
        return value + 1;
    }

    private SwiftSymbol demangleIndexAsName() throws Exception {
        return new SwiftSymbol(SwiftSymbol.Kind.number, null, new Contents(demangleIndex()));
    }

    private <T> T require(T value) throws Exception {
        if (value != null) {
            return value;
        } else {
            scanner.throwException(SymbolScanner.MangledExceptionType.unexpected);
            return null;
        }
    }

    private void require(boolean value) throws Exception {
        if (!value) scanner.throwException(SymbolScanner.MangledExceptionType.unexpected);
    }

    private SwiftSymbol pushMultiSubstitutions(int repeatCount, int index) throws Exception {
        require(repeatCount <= maxRepeatCount);
        SwiftSymbol nd = substitutions.get(index);
        require(nd);
        if (repeatCount > 1) {
            while (repeatCount-- > 1) nameStack.add(nd);
        }
        return nd;
    }

    /*===================================pop options===================================*/

    private SwiftSymbol pop() {
        return nameStack.pop();
    }

    private SwiftSymbol pop(SwiftSymbol.Kind kind) {
        if (nameStack.size() < 1) return null;
        SwiftSymbol last = nameStack.get(nameStack.size() - 1);
        if (last.kind == kind) {
            return pop();
        } else {
            return null;
        }
    }

    private SwiftSymbol pop(DemanglerCallback callback) {
        SwiftSymbol last = nameStack.get(nameStack.size() - 1);
        if (last == null) return null;
        if (callback.dealKind(last.kind)) return pop();
        return null;
    }

    private SwiftSymbol popFunctionType(SwiftSymbol.Kind kind) throws Exception {
        SwiftSymbol name = new SwiftSymbol(kind, new SafeArrayList(), new Contents());
        SwiftSymbol temp = pop(SwiftSymbol.Kind.throwsAnnotation);
        if (temp != null) {
            name.children.add(temp);
        }
        name.children.add(popFunctionParams(SwiftSymbol.Kind.argumentTuple));
        name.children.add(popFunctionParams(SwiftSymbol.Kind.returnType));
        return new SwiftSymbol(SwiftSymbol.Kind.type, name);
    }

    private SwiftSymbol popFunctionParams(SwiftSymbol.Kind kind) throws Exception {
        SwiftSymbol paramsType;
        if (pop(SwiftSymbol.Kind.emptyList) != null) {
            return new SwiftSymbol(kind, new SwiftSymbol(SwiftSymbol.Kind.type, new SwiftSymbol(SwiftSymbol.Kind.tuple, (SwiftSymbol)null)));
        } else {
            paramsType = require(pop(SwiftSymbol.Kind.type));
        }

        if (kind == SwiftSymbol.Kind.argumentTuple) {
            SwiftSymbol params = require(paramsType.children.get(0));
            int numParams = params.kind == SwiftSymbol.Kind.tuple ? params.children.size() : 1;
            return new SwiftSymbol(kind, Utility.CreateLists(paramsType), new Contents(numParams));
        } else {
            return new SwiftSymbol(kind, Utility.CreateLists(paramsType), null);
        }
    }

    private SwiftSymbol getLabel(SwiftSymbol params, int idx) throws Exception {
        if (isOldFunctionTypeMangling) {
            SwiftSymbol param = require(params.children.get(idx));
            SafeArrayList.Enumerator label = ((SafeArrayList<SwiftSymbol>) param.children).First(swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.tupleElementName);
            if (label.element != null) {
                params.children.get(idx).children.remove(label.offset);
                return new SwiftSymbol(SwiftSymbol.Kind.identifier, null, new Contents(label.element.text().isEmpty() ? "" : label.element.text()));
            }
            return new SwiftSymbol(SwiftSymbol.Kind.firstElementMarker, (SwiftSymbol)null);
        }
        return require(pop());
    }

    private SwiftSymbol popFunctionParamLabels(SwiftSymbol type) throws Exception {
        if (!isOldFunctionTypeMangling && pop(SwiftSymbol.Kind.emptyList) != null) {
            return new SwiftSymbol(SwiftSymbol.Kind.labelList, (SwiftSymbol) null);
        }

        if (type.kind != SwiftSymbol.Kind.type) return null;

        SwiftSymbol topFuncType = require(type.children.get(0));
        SwiftSymbol funcType;
        if (topFuncType.kind == SwiftSymbol.Kind.dependentGenericType) {
            funcType = require(topFuncType.children.get(1).children.get(0));
        } else {
            funcType = topFuncType;
        }

        if (!(funcType.kind == SwiftSymbol.Kind.functionType || funcType.kind == SwiftSymbol.Kind.noEscapeFunctionType)) {
            return null;
        }

        SwiftSymbol parameterType = require(funcType.children.get(0));
        if (parameterType.kind == SwiftSymbol.Kind.throwsAnnotation) {
            parameterType = require(funcType.children.get(1));
        }

        require(parameterType.kind == SwiftSymbol.Kind.argumentTuple);
        int index = parameterType.index();
        if (index <= 0) return null;

        SwiftSymbol possibleTuple = parameterType.children.get(0).children.get(0);
        if (isOldFunctionTypeMangling || possibleTuple == null || possibleTuple.kind != SwiftSymbol.Kind.tuple) {
            return new SwiftSymbol(SwiftSymbol.Kind.labelList, (SwiftSymbol)null);
        }

        boolean hasLabels = false;
        List children = new SafeArrayList();
        for (int i = 0; i < index; i++) {
            SwiftSymbol label = getLabel(possibleTuple, i);
            require(label.kind == SwiftSymbol.Kind.identifier || label.kind == SwiftSymbol.Kind.firstElementMarker);
            children.add(label);
            hasLabels = hasLabels || (label.kind != SwiftSymbol.Kind.firstElementMarker);
        }

        if (!hasLabels) {
            return new SwiftSymbol(SwiftSymbol.Kind.labelList, (SwiftSymbol)null);
        }
        if (!isOldFunctionTypeMangling) Collections.reverse(children);
        return new SwiftSymbol(SwiftSymbol.Kind.labelList, children, null);
    }

    private SwiftSymbol popTuple() throws Exception {
        List children = new SafeArrayList();
        if (pop(SwiftSymbol.Kind.emptyList) == null) {
            boolean firstElem = false;
            do {
                firstElem = pop(SwiftSymbol.Kind.firstElementMarker) != null;
                SwiftSymbol poped = pop(SwiftSymbol.Kind.variadicMarker);
                List elemChildren = poped != null ? Utility.CreateLists(poped) : new SafeArrayList();
                SwiftSymbol ident = pop(SwiftSymbol.Kind.identifier);
                if (ident != null && ident.contents.type == Contents.Type.STRRING) {
                    String text = ident.contents.getName();
                    elemChildren.add(new SwiftSymbol(SwiftSymbol.Kind.tupleElementName, new SafeArrayList(), new Contents(text)));
                }
                elemChildren.add(require(pop(SwiftSymbol.Kind.type)));
                children.add(0, new SwiftSymbol(SwiftSymbol.Kind.tupleElement, elemChildren, new Contents()));
            } while (!firstElem);
        }
        return new SwiftSymbol(SwiftSymbol.Kind.tuple, children);
    }

    private SwiftSymbol popTypeList() throws Exception {
        List children = new SafeArrayList();
        if (pop(SwiftSymbol.Kind.emptyList) == null) {
            boolean firstElem = false;
            do {
                firstElem = pop(SwiftSymbol.Kind.firstElementMarker) != null;
                children.add(0, require(pop(SwiftSymbol.Kind.type)));
            } while (!firstElem);
        }
        return new SwiftSymbol(SwiftSymbol.Kind.typeList, children, null);
    }

    private SwiftSymbol popProtocol() throws Exception {
        SwiftSymbol type = pop(SwiftSymbol.Kind.type);
        if (type != null) {
            require(type.children.get(0).kind == SwiftSymbol.Kind.protocol);
            return type;
        }
        SwiftSymbol name = require(pop(kind -> kind.isDeclName()));
        SwiftSymbol context = popContext();
        return new SwiftSymbol(SwiftSymbol.Kind.protocol, Utility.CreateLists(context, name));
    }

    private SwiftSymbol popModule() {
        SwiftSymbol ident = pop(SwiftSymbol.Kind.identifier);
        if (ident != null) {
            return ident.changeKind(SwiftSymbol.Kind.module, new SafeArrayList());
        } else {
            return pop(SwiftSymbol.Kind.module);
        }
    }

    private SwiftSymbol popContext() throws Exception {
        SwiftSymbol mod = popModule();
        if (mod != null) return mod;
        SwiftSymbol type = pop(SwiftSymbol.Kind.type);
        if (type != null) {
            SwiftSymbol child = require(type.children.get(0));
            require(child.kind.isContext());
            return child;
        }
        return require(pop(kind -> kind.isContext()));
    }

    private SwiftSymbol popTypeAndGetChild() throws Exception {
        return require(pop(SwiftSymbol.Kind.type).children.get(0));
    }

    private SwiftSymbol popTypeAndGetAnyGeneric() throws Exception {
        SwiftSymbol child = popTypeAndGetChild();
        require(child.kind.isAnyGeneric());
        return child;
    }

    private SwiftSymbol popAssociatedTypeName() throws Exception {
        SwiftSymbol proto = pop(SwiftSymbol.Kind.type);
        SwiftSymbol id = require(pop(SwiftSymbol.Kind.identifier));
        SwiftSymbol result = id.changeKind(SwiftSymbol.Kind.dependentAssociatedTypeRef, new SafeArrayList());
        if (proto != null) {
            require(proto.children.get(0).kind == SwiftSymbol.Kind.protocol);
            result.children.add(proto);
        }
        return result;
    }

    private SwiftSymbol popAssociatedTypePath() throws Exception {
        boolean firstElem = false;
        List assocTypePath = new SafeArrayList();
        do {
            firstElem = pop(SwiftSymbol.Kind.firstElementMarker) != null;
            assocTypePath.add(require(pop(kind -> kind.isDeclName())));
        } while (!firstElem);
        Collections.reverse(assocTypePath);
        return new SwiftSymbol(SwiftSymbol.Kind.assocTypePath, assocTypePath, null);
    }

    private SwiftSymbol popProtocolConformance() throws Exception {
        SwiftSymbol genSig = pop(SwiftSymbol.Kind.dependentGenericSignature);
        SwiftSymbol module = require(popModule());
        SwiftSymbol proto = popProtocol();
        SwiftSymbol type_ = pop(SwiftSymbol.Kind.type);
        SwiftSymbol ident = null;
        if (type_ == null) {
            ident = pop(SwiftSymbol.Kind.identifier);
            type_ = pop(SwiftSymbol.Kind.type);
        }
        if (genSig != null) {
            type_ = new SwiftSymbol(SwiftSymbol.Kind.dependentGenericType, Utility.CreateLists(genSig, require(type_)));
        }
        List children = Utility.CreateLists(require(type_), proto, module);
        if (ident != null) {
            children.add(ident);
        }
        return new SwiftSymbol(SwiftSymbol.Kind.protocolConformance, children, null);
    }

    /*===================================deMangle options===================================*/

    private SwiftSymbol getDependentGenericParamType(int depeth, int index) throws Exception {
        require(depeth >= 0 && index >= 0);
        int charIndex = index;
        String name = "";
        do {
            char offset = (char)('A' + (charIndex % 26));
            name = name + require(offset);
            charIndex /= 26;
        } while (charIndex != 0);

        if (depeth != 0) {
            name = name + depeth;
        }

        SwiftSymbol child1 = new SwiftSymbol(SwiftSymbol.Kind.index, new SafeArrayList(), new Contents(depeth));
        SwiftSymbol child2 = new SwiftSymbol(SwiftSymbol.Kind.index, new SafeArrayList(), new Contents(index));
        return new SwiftSymbol(SwiftSymbol.Kind.dependentGenericParamType, Utility.CreateLists(child1, child2), new Contents(name));
    }

    private SwiftSymbol demangleStandardSubstitution() throws Exception {
        switch (scanner.readChar()) {
            case 'o':
                return new SwiftSymbol(SwiftSymbol.Kind.module, new SafeArrayList(), new Contents(objcModule));
            case 'C':
                return new SwiftSymbol(SwiftSymbol.Kind.module, new SafeArrayList(), new Contents(cModule));
            case 'g': {
                SwiftSymbol child1 = new SwiftSymbol(SwiftSymbol.Kind.theEnum, "Optional");
                SwiftSymbol child2 = new SwiftSymbol(SwiftSymbol.Kind.typeList, require(pop(SwiftSymbol.Kind.type)));
                SwiftSymbol op = new SwiftSymbol(SwiftSymbol.Kind.boundGenericEnum, Utility.CreateLists(child1, child2));
                substitutions.add(op);
                return op;
            }
            default: {
                scanner.backtrack(1);
                int dn = demangleNatural();
                int repeatCount = dn > 0 ? dn : 0;
                require(repeatCount <= maxRepeatCount);
                SwiftSymbol nd = null;
                switch (scanner.readChar()) {
                    case 'a':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Array");
                        break;
                    case 'A':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "AutoreleasingUnsafeMutablePointer");
                        break;
                    case 'b':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Bool");
                        break;
                    case 'c':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnicodeScalar");
                        break;
                    case 'D':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Dictionary");
                        break;
                    case 'd':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Double");
                        break;
                    case 'f':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Float");
                        break;
                    case 'h':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Set");
                        break;
                    case 'I':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "DefaultIndices");
                        break;
                    case 'i':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Int");
                        break;
                    case 'J':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Character");
                        break;
                    case 'N':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "ClosedRange");
                        break;
                    case 'n':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Range");
                        break;
                    case 'O':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "ObjectIdentifier");
                        break;
                    case 'p':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeMutablePointer");
                        break;
                    case 'P':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafePointer");
                        break;
                    case 'R':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeBufferPointer");
                        break;
                    case 'r':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeMutableBufferPointer");
                        break;
                    case 'S':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "String");
                        break;
                    case 's':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "Substring");
                        break;
                    case 'u':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UInt");
                        break;
                    case 'v':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeMutableRawPointer");
                        break;
                    case 'V':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeRawPointer");
                        break;
                    case 'W':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeRawBufferPointer");
                        break;
                    case 'w':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.structure, "UnsafeMutableRawBufferPointer");
                        break;
                    case 'q':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.theEnum, "Optional");
                        break;
                    case 'B':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Optional");
                        break;
                    case 'E':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Encodable");
                        break;
                    case 'e':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Decodable");
                        break;
                    case 'F':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "FloatingPoint");
                        break;
                    case 'G':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "RandomNumberGenerator");
                        break;
                    case 'H':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Hashable");
                        break;
                    case 'j':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Numeric");
                        break;
                    case 'K':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "BidirectionalCollection");
                        break;
                    case 'k':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "RandomAccessCollection");
                        break;
                    case 'L':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Comparable");
                        break;
                    case 'l':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Collection");
                        break;
                    case 'M':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "MutableCollection");
                        break;
                    case 'm':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "RangeReplaceableCollection");
                        break;
                    case 'Q':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Equatable");
                        break;
                    case 'T':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Sequence");
                        break;
                    case 't':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "IteratorProtocol");
                        break;
                    case 'U':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "UnsignedInteger");
                        break;
                    case 'X':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "RangeExpression");
                        break;
                    case 'x':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "Strideable");
                        break;
                    case 'Y':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "RawRepresentable");
                        break;
                    case 'y':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "StringProtocol");
                        break;
                    case 'Z':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "SignedInteger");
                        break;
                    case 'z':
                        nd = new SwiftSymbol(SwiftSymbol.Kind.protocol, "BinaryInteger");
                        break;
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
                if (repeatCount > 1) {
                    for (int i = 0; i < repeatCount - 1; i++) {
                        if (nd != null) nameStack.add(nd);
                    }
                }
                return nd;
            }
        }
    }

    private SwiftSymbol demangleIdentifier() throws Exception {
        boolean hasWordSubs = false;
        boolean isPunycoded = false;
        char c = scanner.read(c1 -> Character.isDigit(c1));
        if (c == '0') {
            if (scanner.readChar() == '0') {
                isPunycoded = true;
            } else {
                scanner.backtrack(1);
                hasWordSubs = true;
            }
        } else {
            scanner.backtrack(1);
        }

        String identifier = "";
        do {
            while (hasWordSubs && Character.isLetter(scanner.peek(0))) {
                char c_ = scanner.readChar();
                int wordIndex = 0;
                if (Character.isLowerCase(c_)) {
                    wordIndex = c_ - 'a';
                } else {
                    wordIndex = c_ - 'A';
                    hasWordSubs = false;
                }
                require(wordIndex < maxNumWords);
                identifier = identifier + require(words.get(wordIndex));
            }
            if (scanner.conditional("0")) break;
            int numChars = require(demangleNatural());
            require(numChars > 0);
            if (isPunycoded) {
                scanner.conditional("_");
            }
            String text = scanner.readChars(numChars);
            if (isPunycoded) {
                identifier += decodeSwiftPunycode(text);
            } else {
                identifier += text;
                String word = null;
                for (char c__ : text.toCharArray()) {
                    if (word == null && !Character.isDigit(c__) && c__ != '_' && words.size() < maxNumWords) {
                        word = String.valueOf(c__);
                    } else if (word != null) {
                        String w = word;
                        char[] charArr = w.toCharArray();
                        if (c__ == '_' || (Character.isUpperCase(charArr[charArr.length - 1]) == false && Character.isUpperCase(c__))) {
                            if (w.length() >= 2) {
                                words.add(w);
                            }
                            if (!Character.isDigit(c__) && c__ != '_' && word.length() < maxNumWords) {
                                word = String.valueOf(c__);
                            } else {
                                word = null;
                            }
                        } else {
                            word += c__;
                        }
                    }
                }
                if (word != null && word.length() >= 2) {
                    words.add(word);
                }
            }
        } while (hasWordSubs);
        require(!identifier.isEmpty());
        SwiftSymbol result = new SwiftSymbol(SwiftSymbol.Kind.identifier, new SafeArrayList(), new Contents(identifier));
        substitutions.add(result);
        return result;
    }

    private SwiftSymbol demangleOperatorIdentifier() throws Exception {
        SwiftSymbol ident = require(pop(SwiftSymbol.Kind.identifier));
        char[] opCharTable = "& @/= >    <*!|+?%-~   ^ .".toCharArray();
        String str = "";
        for (char c : require(ident.text()).toCharArray()) {
            //is ASCII
            if (c > 0 && c < 127) {
                str += c;
            } else {
                require(Character.isLowerCase(c));
                char O = require(opCharTable[c - 'a']);
                require(O != ' ');
                str += O;
            }
        }
        switch (scanner.readChar()) {
            case 'i':
                return new SwiftSymbol(SwiftSymbol.Kind.infixOperator, null, new Contents(str));
            case 'p':
                return new SwiftSymbol(SwiftSymbol.Kind.prefixOperator, null, new Contents(str));
            case 'P':
                return new SwiftSymbol(SwiftSymbol.Kind.postfixOperator, null, new Contents(str));
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.searchFailed);
        }
        return null;
    }

    private SwiftSymbol demangleLocalIdentifier() throws Exception {
        char c = scanner.readChar();
        if (c == 'L') {
            SwiftSymbol discriminator = require(pop(SwiftSymbol.Kind.identifier));
            SwiftSymbol name = require(pop(kind -> kind.isDeclName()));
            return new SwiftSymbol(SwiftSymbol.Kind.privateDeclName, Utility.CreateLists(discriminator, name), null);
        }
        if (c == 'l') {
            SwiftSymbol discriminator = require(pop(SwiftSymbol.Kind.identifier));
            return new SwiftSymbol(SwiftSymbol.Kind.privateDeclName, Utility.CreateLists(discriminator), null);
        }
        if ((c >= 'a' && c <= 'j') || (c >= 'A' && c <= 'J')) {
            return new SwiftSymbol(SwiftSymbol.Kind.relatedEntityDeclName, Utility.CreateLists(require(pop())), new Contents(String.valueOf(c)));
        }
        scanner.backtrack(1);
        SwiftSymbol discriminator = demangleIndexAsName();
        SwiftSymbol name = require(pop(kind -> kind.isDeclName()));
        return new SwiftSymbol(SwiftSymbol.Kind.localDeclName, Utility.CreateLists(discriminator, name), null);
    }

    private SwiftSymbol demangleBuiltinType() throws Exception {
        int maxTypeSize = 4096;
        char c = scanner.readChar();
        switch (c) {
            case 'b':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.BridgeObject");
            case 'B':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.UnsafeValueBuffer");
            case 'f': {
                int size = demangleIndex() - 1;
                require(size > 0 && size <= maxTypeSize);
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.Float" + size);
            }
            case 'i': {
                int size = demangleIndex() - 1;
                require(size > 0 && size <= maxTypeSize);
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.Int" + size);
            }
            case 'v': {
                int elts = demangleIndex() - 1;
                require(elts > 0 && elts <= maxTypeSize);
                SwiftSymbol eltType = popTypeAndGetChild();
                String text = require(eltType.text());
                require(eltType.kind == SwiftSymbol.Kind.builtinTypeName && text.startsWith("Builtin."));
                String name = text.substring("Builtin.".length());
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, ("Builtin.Vec" + elts + "x" + name));
            }
            case 'O':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.UnknownObject");
            case 'o':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.NativeObject");
            case 'p':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.RawPointer");
            case 't':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.SILToken");
            case 'w':
                return SwiftSymbol.SwiftSymbolWithBuiltinType(SwiftSymbol.Kind.builtinTypeName, "Builtin.Word");
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
        }
        return null;
    }

    private SwiftSymbol demangleAnyGenericType(SwiftSymbol.Kind kind) throws Exception {
        SwiftSymbol name = require(pop(kind1 -> kind1.isDeclName()));
        SwiftSymbol ctx = popContext();
        SwiftSymbol type = new SwiftSymbol(kind, Utility.CreateLists(ctx, name));
        substitutions.add(type);
        return type;
    }

    private SwiftSymbol demangleExtensionContext() throws Exception {
        SwiftSymbol genSig = pop(SwiftSymbol.Kind.dependentGenericSignature);
        SwiftSymbol modle = require(popModule());
        SwiftSymbol type = popTypeAndGetAnyGeneric();
        if (genSig != null) {
            return new SwiftSymbol(SwiftSymbol.Kind.extension, Utility.CreateLists(modle, type, genSig), null);
        } else {
            return new SwiftSymbol(SwiftSymbol.Kind.extension, Utility.CreateLists(modle, type), null);
        }
    }

    private SwiftSymbol demanglePlainFunction() throws Exception {
        SwiftSymbol genSig = pop(SwiftSymbol.Kind.dependentGenericSignature);
        SwiftSymbol type = popFunctionType(SwiftSymbol.Kind.functionType);
        SwiftSymbol labelList = popFunctionParamLabels(type);
        if (genSig != null) {
            type = new SwiftSymbol(SwiftSymbol.Kind.dependentGenericType, Utility.CreateLists(genSig, type));
        }
        SwiftSymbol name = require(pop(kind -> kind.isDeclName()));
        SwiftSymbol ctx = popContext();
        if (labelList != null) {
            return new SwiftSymbol(SwiftSymbol.Kind.function, Utility.CreateLists(ctx, name, labelList, type), new Contents());
        }
        return new SwiftSymbol(SwiftSymbol.Kind.function, Utility.CreateLists(ctx, name, type), new Contents());
    }

    private SwiftSymbol demangleRetroactiveConformance() throws Exception {
        SwiftSymbol index = demangleIndexAsName();
        SwiftSymbol conformance = popProtocolConformance();
        return new SwiftSymbol(SwiftSymbol.Kind.retroactiveConformance, Utility.CreateLists(index, conformance), new Contents());
    }

    private SwiftSymbol demangleBoundGenericType() throws Exception {
        List retroactiveConformances = new SafeArrayList();
        while (true) {
            SwiftSymbol conformance = pop(SwiftSymbol.Kind.retroactiveConformance);
            if (conformance != null) {
                retroactiveConformances.add(conformance);
            } else {
                break;
            }
        }
        List array = new SafeArrayList();
        while (true) {
            List children = new SafeArrayList();
            while (true) {
                SwiftSymbol t = pop(SwiftSymbol.Kind.type);
                if (t != null) {
                    children.add(t);
                } else {
                    break;
                }
            }
            array.add(new SwiftSymbol(SwiftSymbol.Kind.typeList, Utility.ReverseListWithoutChange(children), new Contents()));
            if (pop(SwiftSymbol.Kind.emptyList) != null) {
                break;
            } else {
                require(pop(SwiftSymbol.Kind.firstElementMarker));
            }
        }
        SwiftSymbol nominal = popTypeAndGetAnyGeneric();
        List children = Utility.CreateLists(demangleBoundGenericArgs(nominal, array, 0));
        if (!retroactiveConformances.isEmpty()) {
            children.add(new SwiftSymbol(SwiftSymbol.Kind.typeList, Utility.ReverseListWithoutChange(retroactiveConformances), null));
        }
        SwiftSymbol type = new SwiftSymbol(SwiftSymbol.Kind.type, children, null);
        substitutions.add(type);
        return type;
    }

    private SwiftSymbol demangleBoundGenericArgs(SwiftSymbol nominal, List<SwiftSymbol> array, int index) throws Exception {
        if (nominal.kind == SwiftSymbol.Kind.symbolicReference || nominal.kind == SwiftSymbol.Kind.unresolvedSymbolicReference) {
            List remainingTypeList = new SafeArrayList();
            for (int i = array.size() - 1; i >= index; --i) {
                SwiftSymbol s = array.get(i);
                for (SwiftSymbol child : s.children) {
                    remainingTypeList.add(child);
                }
            }
            SwiftSymbol child1 = new SwiftSymbol(SwiftSymbol.Kind.type, nominal);
            SwiftSymbol child2 = new SwiftSymbol(SwiftSymbol.Kind.typeList, remainingTypeList, null);
            return new SwiftSymbol(SwiftSymbol.Kind.boundGenericOtherNominalType, Utility.CreateLists(child1, child2), null);
        }

        SwiftSymbol context = require(nominal.children.get(0));
        boolean consumesGenericArgs = true;
        switch (nominal.kind) {
            case variable:
            case explicitClosure:
            case subscript:
                consumesGenericArgs = false;
                break;
            default:
                consumesGenericArgs = true;
        }
        SwiftSymbol args = require(array.get(index));
        SwiftSymbol n = null;
        int offsetIndex = index + (consumesGenericArgs ? 1 : 0);
        if (offsetIndex < array.size()) {
            SwiftSymbol boundParent = null;
            if (context.kind == SwiftSymbol.Kind.extension) {
                SwiftSymbol p = demangleBoundGenericArgs(require(context.children.get(1)), array, offsetIndex);
                boundParent = new SwiftSymbol(SwiftSymbol.Kind.extension, Utility.CreateLists(require(context.children.get(0)), p), null);
                SwiftSymbol thirdChild = context.children.get(2);
                if (thirdChild != null) {
                    boundParent.children.add(thirdChild);
                }
            } else {
                boundParent = demangleBoundGenericArgs(context, array, offsetIndex);
            }
            SafeArrayList newArr = new SafeArrayList();
            newArr.add(boundParent);
            newArr.addAll(Utility.dropFirst(boundParent.children));
            n = new SwiftSymbol(nominal.kind,newArr, null);
        } else {
            n = nominal;
        }

        if (!consumesGenericArgs || args.children.size() == 0) {
            return n;
        }

        SwiftSymbol.Kind kind = null;
        switch (n.kind) {
            case theClass:
                kind = SwiftSymbol.Kind.boundGenericClass;
                break;
            case structure:
                kind = SwiftSymbol.Kind.boundGenericStructure;
                break;
            case theEnum:
                kind = SwiftSymbol.Kind.boundGenericEnum;
                break;
            case protocol:
                kind = SwiftSymbol.Kind.boundGenericProtocol;
                break;
            case otherNominalType:
                kind = SwiftSymbol.Kind.boundGenericOtherNominalType;
                break;
            case typeAlias:
                kind = SwiftSymbol.Kind.boundGenericTypeAlias;
                break;
            case function:
            case constructor: {
                return new SwiftSymbol(SwiftSymbol.Kind.boundGenericFunction, Utility.CreateLists(n, args), null);
            }
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
        }
        SwiftSymbol child = new SwiftSymbol(SwiftSymbol.Kind.type, n);
        return new SwiftSymbol(kind, Utility.CreateLists(child, args), null);
    }

    private SwiftSymbol demangleImplParamConvention() throws Exception {
        String attr = "";
        switch (scanner.readChar()) {
            case 'i':
                attr = "@in";
                break;
            case 'c':
                attr = "@in_constant";
                break;
            case 'l':
                attr = "@inout";
                break;
            case 'b':
                attr = "@inout_aliasable";
                break;
            case 'n':
                attr = "@in_guaranteed";
                break;
            case 'x':
                attr = "@owned";
                break;
            case 'g':
                attr = "@guaranteed";
                break;
            case 'e':
                attr = "@deallocating";
                break;
            case 'y':
                attr = "@unowned";
                break;
            default:
                scanner.backtrack(1);
                return null;
        }
        return new SwiftSymbol(SwiftSymbol.Kind.implParameter, new SwiftSymbol(SwiftSymbol.Kind.implConvention, null, new Contents(attr)));
    }

    private SwiftSymbol demangleImplResultConvention(SwiftSymbol.Kind kind) throws Exception {
        String attr = "";
        switch (scanner.readChar()) {
            case 'r':
                attr = "@out";
                break;
            case 'o':
                attr = "@owned";
                break;
            case 'd':
                attr = "@unowned";
                break;
            case 'u':
                attr = "@unowned_inner_pointer";
                break;
            case 'a':
                attr = "@autoreleased";
                break;
            default:
                scanner.backtrack(1);
                return null;
        }
        return new SwiftSymbol(kind, new SwiftSymbol(SwiftSymbol.Kind.implConvention, new SafeArrayList(), new Contents(attr)));
    }

    private SwiftSymbol demangleImplFunctionType() throws Exception {
        SafeArrayList<SwiftSymbol> typeChildren = new SafeArrayList<SwiftSymbol>();
        SwiftSymbol genSig = pop(SwiftSymbol.Kind.dependentGenericSignature);
        if (genSig != null && scanner.conditional("P")) {
            genSig = genSig.changeKind(SwiftSymbol.Kind.dependentPseudogenericSignature, new SafeArrayList());
        }
        if (scanner.conditional("e")) {
            typeChildren.add(new SwiftSymbol(SwiftSymbol.Kind.implEscaping, (SwiftSymbol) null));
        }

        String cAttr = "";
        switch (scanner.readChar()) {
            case 'y':
                cAttr = "@callee_unowned";
                break;
            case 'g':
                cAttr = "@callee_guaranteed";
                break;
            case 'x':
                cAttr = "@callee_owned";
                break;
            case 't':
                cAttr = "@convention(thin)";
                break;
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
        }
        typeChildren.add(new SwiftSymbol(SwiftSymbol.Kind.implConvention, null, new Contents(cAttr)));

        String fAttr = "";
        switch (scanner.readChar()) {
            case 'B':
                fAttr = "@convention(block)";
                break;
            case 'C':
                fAttr = "@convention(c)";
                break;
            case 'M':
                fAttr = "@convention(method)";
                break;
            case 'O':
                fAttr = "@convention(objc_method)";
                break;
            case 'K':
                fAttr = "@convention(closure)";
                break;
            case 'W':
                fAttr = "@convention(witness_method)";
                break;
            default:
                scanner.backtrack(1);
                fAttr = null;
        }
        if (fAttr != null && !fAttr.isEmpty()) {
            typeChildren.add(new SwiftSymbol(SwiftSymbol.Kind.implFunctionAttribute, null, new Contents(fAttr)));
        }

        if (genSig != null) {
            typeChildren.add(genSig);
        }

        int numTypesToAdd = 0;

        while (true) {
            SwiftSymbol param = demangleImplParamConvention();
            if (param != null) {
                typeChildren.add(param);
                numTypesToAdd += 1;
            }else {
                break;
            }
        }

        while (true) {
            SwiftSymbol result = demangleImplResultConvention(SwiftSymbol.Kind.implResult);
            if (result != null) {
                typeChildren.add(result);
                numTypesToAdd += 1;
            }else {
                break;
            }
        }

        if (scanner.conditional("z")) {
            typeChildren.add(require(demangleImplResultConvention(SwiftSymbol.Kind.implErrorResult)));
            numTypesToAdd += 1;
        }
        scanner.match('_');
        for (int i = 0; i < numTypesToAdd; i++) {
            require(typeChildren.size() - i - 1 >= 0 && typeChildren.size() - i - 1 < typeChildren.size());
            typeChildren.get(typeChildren.size() - i - 1).children.add(require(pop(SwiftSymbol.Kind.type)));
        }
        return new SwiftSymbol(SwiftSymbol.Kind.implFunctionType, typeChildren);
    }

    private SwiftSymbol demangleMetatype() throws Exception {
        switch (scanner.readChar()) {
            case 'c':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolConformanceDescriptor, require(popProtocolConformance()));
            case 'f':
                return new SwiftSymbol(SwiftSymbol.Kind.fullTypeMetadata, require(pop(SwiftSymbol.Kind.type)));
            case 'P':
                return new SwiftSymbol(SwiftSymbol.Kind.genericTypeMetadataPattern, require(pop(SwiftSymbol.Kind.type)));
            case 'a':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadataAccessFunction, require(pop(SwiftSymbol.Kind.type)));
            case 'I':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadataInstantiationCache, require(pop(SwiftSymbol.Kind.type)));
            case 'i':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadataInstantiationFunction, require(pop(SwiftSymbol.Kind.type)));
            case 'r':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadataCompletionFunction, require(pop(SwiftSymbol.Kind.type)));
            case 'l':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadataSingletonInitializationCache, require(pop(SwiftSymbol.Kind.type)));
            case 'L':
                return new SwiftSymbol(SwiftSymbol.Kind.typeMetadataLazyCache, require(pop(SwiftSymbol.Kind.type)));
            case 'm':
                return new SwiftSymbol(SwiftSymbol.Kind.metaclass, require(pop(SwiftSymbol.Kind.type)));
            case 'n':
                return new SwiftSymbol(SwiftSymbol.Kind.nominalTypeDescriptor, require(pop(SwiftSymbol.Kind.type)));
            case 'o':
                return new SwiftSymbol(SwiftSymbol.Kind.classMetadataBaseOffset, require(pop(SwiftSymbol.Kind.type)));
            case 'p':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolDescriptor, popProtocol());
            case 'u':
                return new SwiftSymbol(SwiftSymbol.Kind.methodLookupFunction, popProtocol());
            case 'B':
                return new SwiftSymbol(SwiftSymbol.Kind.reflectionMetadataBuiltinDescriptor, require(pop(SwiftSymbol.Kind.type)));
            case 'F':
                return new SwiftSymbol(SwiftSymbol.Kind.reflectionMetadataFieldDescriptor, require(pop(SwiftSymbol.Kind.type)));
            case 'A':
                return new SwiftSymbol(SwiftSymbol.Kind.reflectionMetadataAssocTypeDescriptor, require(popProtocolConformance()));
            case 'C': {
                SwiftSymbol t = require(pop(SwiftSymbol.Kind.type));
                require(t.children.get(0).kind.isAnyGeneric() == true);
                SwiftSymbol swiftSymbol = new SwiftSymbol(SwiftSymbol.Kind.reflectionMetadataSuperclassDescriptor, require(t.children.get(0)));
                return swiftSymbol;
            }
            case 'V':
                return new SwiftSymbol(SwiftSymbol.Kind.propertyDescriptor, require(pop(kind -> kind.isEntity())));
            case 'X':
                return demanglePrivateContextDescriptor();
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
        }
    }

    private SwiftSymbol demanglePrivateContextDescriptor() throws Exception {
        switch (scanner.readChar()) {
            case 'E':
                return new SwiftSymbol(SwiftSymbol.Kind.extensionDescriptor, popContext());
            case 'M':
                return new SwiftSymbol(SwiftSymbol.Kind.moduleDescriptor, require(popModule()));
            case 'Y': {
                SwiftSymbol discriminator = require(pop());
                SwiftSymbol context = popContext();
                return new SwiftSymbol(SwiftSymbol.Kind.anonymousDescriptor, Utility.CreateLists(context, discriminator), null);
            }
            case 'X': {
               return new SwiftSymbol(SwiftSymbol.Kind.anonymousDescriptor, popContext());
            }
            case 'A': {
                SwiftSymbol path = require(popAssociatedTypePath());
                SwiftSymbol base = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.associatedTypeGenericParamRef, Utility.CreateLists(base, path), null);
            }
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
        }
    }

    private SwiftSymbol demangleArchetype() throws Exception {
        switch (scanner.readChar()) {
            case 'a': {
                SwiftSymbol ident = require(pop(SwiftSymbol.Kind.identifier));
                SwiftSymbol arch = popTypeAndGetChild();
                SwiftSymbol assoc = new SwiftSymbol(SwiftSymbol.Kind.associatedTypeRef, Utility.CreateLists(arch, ident));
                substitutions.add(assoc);
                return assoc;
            }
            case 'y': {
                SwiftSymbol t = demangleAssociatedTypeSimple(demangleGenericParamIndex());
                substitutions.add(t);
                return t;
            }
            case 'z': {
                SwiftSymbol t = demangleAssociatedTypeSimple(getDependentGenericParamType(0, 0));
                substitutions.add(t);
                return t;
            }
            case 'Y': {
                SwiftSymbol t = demangleAssociatedTypeCompound(demangleGenericParamIndex());
                substitutions.add(t);
                return t;
            }
            case 'Z': {
                SwiftSymbol t = demangleAssociatedTypeCompound(getDependentGenericParamType(0, 0));
                substitutions.add(t);
                return t;
            }
            default: {
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
            }
        }
    }

    private SwiftSymbol demangleAssociatedTypeSimple(SwiftSymbol index) throws Exception {
        SwiftSymbol gpi = new SwiftSymbol(SwiftSymbol.Kind.type, index);
        SwiftSymbol atName = popAssociatedTypeName();
        return new SwiftSymbol(SwiftSymbol.Kind.dependentMemberType, Utility.CreateLists(gpi, atName));
    }

    private SwiftSymbol demangleAssociatedTypeCompound(SwiftSymbol index) throws Exception {
        SafeArrayList<SwiftSymbol> assocTypeNames = new SafeArrayList();
        boolean firstElem = false;
        do {
            firstElem = pop(SwiftSymbol.Kind.firstElementMarker) != null;
            assocTypeNames.add(popAssociatedTypeName());
        } while (!firstElem);
        SwiftSymbol base = index;
        for (int i = assocTypeNames.size() - 1; i >= 0; --i) {
            SwiftSymbol assocType = assocTypeNames.get(i);
            if (assocType != null) {
                base = new SwiftSymbol(SwiftSymbol.Kind.dependentMemberType, Utility.CreateLists(new SwiftSymbol(SwiftSymbol.Kind.type, base), assocType), null);
            }
        }
        return new SwiftSymbol(SwiftSymbol.Kind.type, base);
    }

    private SwiftSymbol demangleGenericParamIndex() throws Exception {
        if (scanner.conditional("d")) {
            int depth = demangleIndex() + 1;
            int index = demangleIndex();
            return getDependentGenericParamType(depth, index);
        } else if (scanner.conditional("z")) {
            return getDependentGenericParamType(0, 0);
        } else {
            return getDependentGenericParamType(0, demangleIndex() + 1);
        }
    }

    private SwiftSymbol demangleThunkOrSpecialization() throws Exception {
        char c = scanner.readChar();
        switch (c) {
            case 'c':
                return new SwiftSymbol(SwiftSymbol.Kind.curryThunk, require(pop(kind -> kind.isEntity())));
            case 'j':
                return new SwiftSymbol(SwiftSymbol.Kind.dispatchThunk, require(pop(kind -> kind.isEntity())));
            case 'q':
                return new SwiftSymbol(SwiftSymbol.Kind.methodDescriptor, require(pop(kind -> kind.isEntity())));
            case 'o':
                return new SwiftSymbol(SwiftSymbol.Kind.objCAttribute, (SwiftSymbol) null);
            case 'O':
                return new SwiftSymbol(SwiftSymbol.Kind.nonObjCAttribute, (SwiftSymbol) null);
            case 'D':
                return new SwiftSymbol(SwiftSymbol.Kind.dynamicAttribute, (SwiftSymbol) null);
            case 'd':
                return new SwiftSymbol(SwiftSymbol.Kind.directMethodReferenceAttribute, (SwiftSymbol) null);
            case 'a':
                return new SwiftSymbol(SwiftSymbol.Kind.partialApplyObjCForwarder, (SwiftSymbol) null);
            case 'A':
                return new SwiftSymbol(SwiftSymbol.Kind.partialApplyForwarder, (SwiftSymbol) null);
            case 'm':
                return new SwiftSymbol(SwiftSymbol.Kind.mergedFunction, (SwiftSymbol) null);
            case 'C':
                return new SwiftSymbol(SwiftSymbol.Kind.coroutineContinuationPrototype, require(pop(SwiftSymbol.Kind.type)));
            case 'V': {
                SwiftSymbol base = require(pop(kind -> kind.isEntity()));
                SwiftSymbol derived = require(pop(kind -> kind.isEntity()));
                return new SwiftSymbol(SwiftSymbol.Kind.vTableThunk, Utility.CreateLists(derived, base), null);
            }
            case 'W': {
                SwiftSymbol entity = require(pop(kind -> kind.isEntity()));
                SwiftSymbol conf = popProtocolConformance();
                return new SwiftSymbol(SwiftSymbol.Kind.protocolWitness, Utility.CreateLists(conf, entity), null);
            }
            case 'R':
            case 'r': {
                SwiftSymbol genSig = pop(SwiftSymbol.Kind.dependentGenericSignature);
                SwiftSymbol type2 = require(pop(SwiftSymbol.Kind.type));
                SwiftSymbol type1 = require(pop(SwiftSymbol.Kind.type));
                SwiftSymbol.Kind kind = c == 'R' ? SwiftSymbol.Kind.reabstractionThunkHelper : SwiftSymbol.Kind.reabstractionThunk;
                if (genSig != null) {
                    return new SwiftSymbol(kind, Utility.CreateLists(genSig, type1, type2), null);
                } else {
                    return new SwiftSymbol(kind, Utility.CreateLists(type1, type2), null);
                }
            }
            case 'g':
                return demangleGenericSpecialization(SwiftSymbol.Kind.genericSpecialization);
            case 'G':
                return demangleGenericSpecialization(SwiftSymbol.Kind.genericSpecializationNotReAbstracted);
            case 'P':
            case 'p': {
                SwiftSymbol.Kind kind = c == 'P' ? SwiftSymbol.Kind.genericSpecializationNotReAbstracted : SwiftSymbol.Kind.genericPartialSpecialization;
                SwiftSymbol spec = demangleSpecAttributes(kind, false);
                SwiftSymbol param = new SwiftSymbol(kind, require(pop(SwiftSymbol.Kind.type)));
                spec.children.add(param);
                return spec;
            }
            case 'f':
                return demangleFunctionSpecialization();
            case 'K':
            case 'k': {
                SwiftSymbol.Kind kind = c == 'K' ? SwiftSymbol.Kind.keyPathGetterThunkHelper : SwiftSymbol.Kind.keyPathSetterThunkHelper;
                SafeArrayList<SwiftSymbol> types = new SafeArrayList<>();
                SwiftSymbol node = pop(SwiftSymbol.Kind.type);
                while (node != null) {
                    types.add(node);
                    node = pop(SwiftSymbol.Kind.type);
                }
                SwiftSymbol result = null;
                SwiftSymbol n = pop();
                if (n != null) {
                    if (n.kind == SwiftSymbol.Kind.dependentGenericSignature) {
                        SwiftSymbol decl = require(pop());
                        result = new SwiftSymbol(kind, Utility.CreateLists(decl, n), null);
                    } else {
                        result = new SwiftSymbol(kind, n);
                    }
                } else {
                    scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
                for (SwiftSymbol t : types) {
                    result.children.add(t);
                }
                return result;
            }
            case 'l':
                return new SwiftSymbol(SwiftSymbol.Kind.associatedTypeDescriptor, require(popAssociatedTypeName()));
            case 'L':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolRequirementsBaseDescriptor, require(popProtocol()));
            case 'M':
                return new SwiftSymbol(SwiftSymbol.Kind.defaultAssociatedTypeMetadataAccessor, require(popAssociatedTypeName()));
            case 'n': {
                SwiftSymbol requirement = popProtocol();
                SwiftSymbol associatedTypePath = popAssociatedTypePath();
                SwiftSymbol protocolType = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.associatedConformanceDescriptor, Utility.CreateLists(protocolType, associatedTypePath, requirement), null);
            }
            case 'N': {
                SwiftSymbol requirement = popProtocol();
                SwiftSymbol associatedTypePath = popAssociatedTypePath();
                SwiftSymbol protocolType = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.defaultAssociatedConformanceAccessor, Utility.CreateLists(protocolType, associatedTypePath, requirement), null);
            }
            case 'H':
            case 'h': {
                SwiftSymbol.Kind kind = c == 'H' ? SwiftSymbol.Kind.keyPathEqualsThunkHelper : SwiftSymbol.Kind.keyPathHashThunkHelper;
                SafeArrayList<SwiftSymbol> types = new SafeArrayList<>();
                SwiftSymbol node = require(pop());
                SwiftSymbol genericSig = null;
                if (node.kind == SwiftSymbol.Kind.dependentGenericSignature) {
                    genericSig = node;
                } else if (node.kind == SwiftSymbol.Kind.type) {
                    types.add(node);
                } else {
                    scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }

                while (true) {
                    SwiftSymbol n = pop();
                    if (n != null) {
                        require(n.kind == SwiftSymbol.Kind.type);
                        types.add(n);
                    }else  {
                        break;
                    }
                }

                SwiftSymbol result = new SwiftSymbol(kind, (SwiftSymbol) null);
                for (SwiftSymbol t : types) {
                    result.children.add(t);
                }
                if (genericSig != null) {
                    result.children.add(genericSig);
                }
                return result;
            }
            case 'v':
                return new SwiftSymbol(SwiftSymbol.Kind.outlinedVariable, null, new Contents(demangleIndex()));
            case 'e':
                return new SwiftSymbol(SwiftSymbol.Kind.outlinedBridgedMethod, null, new Contents(demangleBridgedMethodParams()));
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
        }
    }

    private String demangleBridgedMethodParams() throws Exception {
        if (scanner.conditional("_")) {
            return "";
        }
        String str = "";
        char kind = scanner.readChar();
        switch (kind) {
            case 'p':
            case 'a':
            case 'm': {
                str += kind;
                break;
            }
            default:
                return "";
        }
        while (!scanner.conditional("_")) {
            char c = scanner.readChar();
            require(c == 'n' || c == 'b');
            str += c;
        }
        return str;
    }

    private SwiftSymbol demangleGenericSpecialization(SwiftSymbol.Kind kind) throws Exception {

        SwiftSymbol spec = demangleSpecAttributes(kind, false);
        SwiftSymbol list = popTypeList();
        for (SwiftSymbol t : list.children) {
            spec.children.add(new SwiftSymbol(SwiftSymbol.Kind.genericSpecializationParam, t));
        }
        return spec;
    }

    private SwiftSymbol demangleFunctionSpecialization() throws Exception {
        SwiftSymbol spec = demangleSpecAttributes(SwiftSymbol.Kind.functionSignatureSpecialization, true);
        int paramIdx = 0;
        while (!scanner.conditional("_")) {
            spec.children.add(demangleFuncSpecParam(paramIdx));
            paramIdx++;
        }
        if (!scanner.conditional("n")) {
            spec.children.add(demangleFuncSpecParam(~0));
        }
        List<SwiftSymbol> reversedList = Utility.ReverseListWithoutChange(spec.children);
        for (int i = 0; i < reversedList.size(); i++) {
            SwiftSymbol param = reversedList.get(i);
            if (param.kind != SwiftSymbol.Kind.functionSignatureSpecializationParam) continue;
            SwiftSymbol kindName = param.children.get(0);
            if (kindName == null) continue;

            if (kindName.kind != SwiftSymbol.Kind.functionSignatureSpecializationParamKind) scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
            int index = -1;
            if (kindName.contents.type == Contents.Type.INT) {
                index = kindName.contents.getIndex();
            }
            SwiftSymbol.FunctionSigSpecializationParamKind kind = SwiftSymbol.FunctionSigSpecializationParamKind.paramKind(index);
            if (kind == null) scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);

            switch (kind) {
                case constantPropFunction:
                case constantPropGlobal:
                case constantPropString:
                case closureProp: {
                    int fixedChildrenEndIndex = param.children.size() - 1;
                    SwiftSymbol t = pop(SwiftSymbol.Kind.type);
                    while (t != null) {
                        require(kind == SwiftSymbol.FunctionSigSpecializationParamKind.closureProp);
                        param.children.add(fixedChildrenEndIndex, t);
                        t = pop(SwiftSymbol.Kind.type);
                    }
                    SwiftSymbol name = require(pop(SwiftSymbol.Kind.identifier));
                    String text = require(name.text());
                    if (kind == SwiftSymbol.FunctionSigSpecializationParamKind.constantPropString && !text.isEmpty() && (text.toCharArray()[0] == '_')) {
                        text = text.substring(1);
                    }
                    param.children.add(fixedChildrenEndIndex, new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamPayload, null, new Contents(text)));
                    spec.children.set(spec.children.size() - 1 - i, param);
                }
                break;
                default:
                    break;
            }
        }
        return spec;
    }

    private SwiftSymbol demangleFuncSpecParam(int index) throws Exception {
        SwiftSymbol param = new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParam, null, new Contents(index));
        switch (scanner.readChar()) {
            case 'n':
                break;
            case 'c':
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.closureProp.getValue())));
                break;
            case 'p':
                switch (scanner.readChar()) {
                    case 'f':
                        param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.constantPropFunction.getValue())));
                        break;
                    case 'g':
                        param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.constantPropGlobal.getValue())));
                        break;
                    case 'i':
                        param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.constantPropInteger.getValue())));
                        break;
                    case 'd':
                        param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.constantPropFloat.getValue())));
                        break;
                    case 's': {
                        String encoding = "";
                        switch (scanner.readChar()) {
                            case 'b':
                                encoding = "u8";
                                break;
                            case 'w':
                                encoding = "u16";
                                break;
                            case 'c':
                                encoding = "objc";
                                break;
                            default: {
                                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                            }
                            param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.constantPropString.getValue())));
                            param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamPayload, null, new Contents(encoding)));
                        }
                    }
                    break;
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
                break;
            case 'e': {
                int value = SwiftSymbol.FunctionSigSpecializationParamKind.existentialToGeneric.getValue();
                if (scanner.conditional("D")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.dead.getValue();
                }
                if (scanner.conditional("G")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.ownedToGuaranteed.getValue();
                }
                if (scanner.conditional("O")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.guaranteedToOwned.getValue();
                }
                if (scanner.conditional("X")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.sroa.getValue();
                }
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(value)));
            }
            break;
            case 'd': {
                int value = SwiftSymbol.FunctionSigSpecializationParamKind.dead.getValue();
                if (scanner.conditional("G")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.ownedToGuaranteed.getValue();
                }
                if (scanner.conditional("O")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.guaranteedToOwned.getValue();
                }
                if (scanner.conditional("X")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.sroa.getValue();
                }
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(value)));
            }
            break;
            case 'g': {
                int value = SwiftSymbol.FunctionSigSpecializationParamKind.ownedToGuaranteed.getValue();
                if (scanner.conditional("O")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.guaranteedToOwned.getValue();
                }
                if (scanner.conditional("X")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.sroa.getValue();
                }
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(value)));
            }
            break;
            case 'o': {
                int value = SwiftSymbol.FunctionSigSpecializationParamKind.guaranteedToOwned.getValue();
                if (scanner.conditional("X")) {
                    value |= SwiftSymbol.FunctionSigSpecializationParamKind.sroa.getValue();
                }
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(value)));
            }
            break;
            case 'x': {
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.sroa.getValue())));
            }
            break;
            case 'i': {
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.boxToValue.getValue())));
            }
            break;
            case 's': {
                param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(SwiftSymbol.FunctionSigSpecializationParamKind.boxToStack.getValue())));
            }
            break;
            default: {
                param = null;
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
            }
        }
        return param;
    }

    private void addFuncSpecParamNumber(SwiftSymbol param, SwiftSymbol.FunctionSigSpecializationParamKind kind) throws Exception {
        param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamKind, null, new Contents(kind.getValue())));
        String str = scanner.readWhile(c -> Character.isDigit(c));
        require(!str.isEmpty());
        param.children.add(new SwiftSymbol(SwiftSymbol.Kind.functionSignatureSpecializationParamPayload, null, new Contents(str)));
    }

    private SwiftSymbol demangleSpecAttributes(SwiftSymbol.Kind kind, boolean demangleUniqueId) throws Exception {
        boolean isFragile = scanner.conditional("q");
        int passId = scanner.readChar() - '0';
        require(0 < passId && 9 > passId);
        Contents contents = new Contents();
        int index = demangleNatural();
        if (demangleUniqueId && index > 0) {
            contents = new Contents(index);
        }
        //TODO: here!
        SwiftSymbol specName = new SwiftSymbol(kind, null, contents);
        if (isFragile) {
            specName.children.add(new SwiftSymbol(SwiftSymbol.Kind.specializationIsFragile, (SwiftSymbol) null));
        }
        specName.children.add(new SwiftSymbol(SwiftSymbol.Kind.specializationPassID, null, new Contents(passId)));
        return specName;
    }

    private SwiftSymbol demangleWitness() throws Exception {
        switch (scanner.readChar()) {
            case 'C':
                return new SwiftSymbol(SwiftSymbol.Kind.enumCase, require(pop(kind -> kind.isEntity())));
            case 'V':
                return new SwiftSymbol(SwiftSymbol.Kind.valueWitnessTable, require(pop(SwiftSymbol.Kind.type)));
            case 'v': {
                int directness = 0;
                switch (scanner.readChar()) {
                    case 'd':
                        directness = SwiftSymbol.Directness.direct.nCode;
                        break;
                    case 'i':
                        directness = SwiftSymbol.Directness.indirect.nCode;
                        break;
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
                return new SwiftSymbol(SwiftSymbol.Kind.fieldOffset, Utility.CreateLists(new SwiftSymbol(SwiftSymbol.Kind.directness, null, new Contents(directness)), require(pop(kind -> kind.isEntity()))), null);
            }
            case 'P':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolWitnessTable, popProtocolConformance());
            case 'p':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolWitnessTablePattern, popProtocolConformance());
            case 'G':
                return new SwiftSymbol(SwiftSymbol.Kind.genericProtocolWitnessTable, popProtocolConformance());
            case 'I':
                return new SwiftSymbol(SwiftSymbol.Kind.genericProtocolWitnessTableInstantiationFunction, popProtocolConformance());
            case 'r':
                return new SwiftSymbol(SwiftSymbol.Kind.resilientProtocolWitnessTable, popProtocolConformance());
            case 'l': {
                SwiftSymbol conf = popProtocolConformance();
                SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.lazyProtocolWitnessTableAccessor, Utility.CreateLists(type, conf), null);
            }
            case 'L': {
                SwiftSymbol conf = popProtocolConformance();
                SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.lazyProtocolWitnessTableCacheVariable, Utility.CreateLists(type, conf), null);
            }
            case 'a':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolWitnessTableAccessor, popProtocolConformance());
            case 't': {
                SwiftSymbol name = require(pop(kind -> kind.isDeclName()));
                SwiftSymbol conf = popProtocolConformance();
                return new SwiftSymbol(SwiftSymbol.Kind.associatedTypeMetadataAccessor, Utility.CreateLists(conf, name), null);
            }
            case 'T': {
                SwiftSymbol protoType = require(pop(SwiftSymbol.Kind.type));
                SwiftSymbol assocTypePath = new SwiftSymbol(SwiftSymbol.Kind.assocTypePath, (SwiftSymbol) null);
                boolean firstElem = false;
                do {
                    firstElem = pop(SwiftSymbol.Kind.firstElementMarker) != null;
                    SwiftSymbol assocType = require(pop(kind -> kind.isDeclName()));
                    assocTypePath.children.add(0, assocType);
                } while (!firstElem);
                return new SwiftSymbol(SwiftSymbol.Kind.associatedTypeWitnessTableAccessor, Utility.CreateLists(popProtocolConformance(), assocTypePath, protoType), null);
            }
            case 'O': {
                SwiftSymbol sig = pop(SwiftSymbol.Kind.dependentGenericSignature);
                SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
                SafeArrayList children = Utility.CreateLists(sig, type);
                switch (scanner.readChar()) {
                    case 'y':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedCopy, children, null);
                    case 'e':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedConsume, children, null);
                    case 'r':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedRetain, children, null);
                    case 's':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedRelease, children, null);
                    case 'b':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedInitializeWithTake, children, null);
                    case 'c':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedInitializeWithCopy, children, null);
                    case 'd':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedAssignWithTake, children, null);
                    case 'f':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedAssignWithCopy, children, null);
                    case 'h':
                        return new SwiftSymbol(SwiftSymbol.Kind.outlinedDestroy, children, null);
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                        return null;
                }
            }
        }
        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
        return null;
    }

    private SwiftSymbol demangleSpecialType() throws Exception {
        char specialChar = scanner.readChar();
        switch (specialChar) {
            case 'E':
                return popFunctionType(SwiftSymbol.Kind.noEscapeFunctionType);
            case 'A':
                return popFunctionType(SwiftSymbol.Kind.escapingAutoClosureType);
            case 'f':
                return popFunctionType(SwiftSymbol.Kind.thinFunctionType);
            case 'K':
                return popFunctionType(SwiftSymbol.Kind.autoClosureType);
            case 'U':
                return popFunctionType(SwiftSymbol.Kind.uncurriedFunctionType);
            case 'B':
                return popFunctionType(SwiftSymbol.Kind.objCBlock);
            case 'C':
                return popFunctionType(SwiftSymbol.Kind.cFunctionPointer);
            case 'o':
                return new SwiftSymbol(SwiftSymbol.Kind.unowned, require(pop(SwiftSymbol.Kind.type)));
            case 'u':
                return new SwiftSymbol(SwiftSymbol.Kind.unmanaged, require(pop(SwiftSymbol.Kind.type)));
            case 'w':
                return new SwiftSymbol(SwiftSymbol.Kind.weak, require(pop(SwiftSymbol.Kind.type)));
            case 'b':
                return new SwiftSymbol(SwiftSymbol.Kind.silBoxType, require(pop(SwiftSymbol.Kind.type)));
            case 'D':
                return new SwiftSymbol(SwiftSymbol.Kind.dynamicSelf, require(pop(SwiftSymbol.Kind.type)));
            case 'M': {
                SwiftSymbol mtr = demangleMetatypeRepresentation();
                SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.metatype, Utility.CreateLists(mtr, type));
            }
            case 'm': {
                SwiftSymbol mtr = demangleMetatypeRepresentation();
                SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
                return new SwiftSymbol(SwiftSymbol.Kind.existentialMetatype, Utility.CreateLists(mtr, type));
            }
            case 'p':
                return new SwiftSymbol(SwiftSymbol.Kind.existentialMetatype, require(pop(SwiftSymbol.Kind.type)));
            case 'c': {
                SwiftSymbol superclass = require(pop(SwiftSymbol.Kind.type));
                SwiftSymbol protocols = demangleProtocolList();
                return new SwiftSymbol(SwiftSymbol.Kind.protocolListWithClass, Utility.CreateLists(protocols, superclass));
            }
            case 'l':
                return new SwiftSymbol(SwiftSymbol.Kind.protocolListWithAnyObject, demangleProtocolList());
            case 'X':
            case 'x': {
                SwiftSymbol X_ss = null;
                SwiftSymbol x_ss = null;
                if (specialChar == 'X') {
                    X_ss = require(pop(SwiftSymbol.Kind.dependentGenericSignature));
                    x_ss = popTypeList();
                }
                SwiftSymbol fieldTypes = popTypeList();
                SwiftSymbol layout = new SwiftSymbol(SwiftSymbol.Kind.silBoxLayout, (SwiftSymbol) null);
                for (SwiftSymbol fieldType : fieldTypes.children) {
                    require(fieldType.kind == SwiftSymbol.Kind.type);
                    SwiftSymbol first = fieldType.children.get(0);
                    if (first != null && first.kind == SwiftSymbol.Kind.inOut) {
                        layout.children.add(new SwiftSymbol(SwiftSymbol.Kind.silBoxMutableField, new SwiftSymbol(SwiftSymbol.Kind.type, require(first.children.get(0)))));
                    } else {
                        layout.children.add(new SwiftSymbol(SwiftSymbol.Kind.silBoxImmutableField, fieldType));
                    }
                }
                SwiftSymbol boxType = new SwiftSymbol(SwiftSymbol.Kind.silBoxTypeWithLayout, layout);
                if (X_ss != null && x_ss != null) {
                    boxType.children.add(X_ss);
                    boxType.children.add(x_ss);
                }
                return new SwiftSymbol(SwiftSymbol.Kind.type, boxType);

            }
            case 'Y':
                return demangleAnyGenericType(SwiftSymbol.Kind.otherNominalType);
            case 'Z': {
                SwiftSymbol types = popTypeList();
                SwiftSymbol name = require(pop(SwiftSymbol.Kind.identifier));
                SwiftSymbol parent = popContext();
                return new SwiftSymbol(SwiftSymbol.Kind.anonymousContext, Utility.CreateLists(name, parent, types), null);
            }
            case 'e':
                return new SwiftSymbol(SwiftSymbol.Kind.type, new SwiftSymbol(SwiftSymbol.Kind.errorType, (SwiftSymbol) null));
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
        }
    }

    private SwiftSymbol demangleMetatypeRepresentation() throws Exception {
        String value = "";
        switch (scanner.readChar()) {
            case 't':
                value = "@thin";
                break;
            case 'T':
                value = "@thick";
                break;
            case 'o':
                value = "@objc_metatype";
                break;
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
        }
        return new SwiftSymbol(SwiftSymbol.Kind.metatypeRepresentation, null, new Contents(value));
    }

    private SwiftSymbol demangleAccessor(SwiftSymbol child) throws Exception {
        SwiftSymbol.Kind kind = null;
        switch (scanner.readChar()) {
            case 'm':
                kind = SwiftSymbol.Kind.materializeForSet;
                break;
            case 's':
                kind = SwiftSymbol.Kind.setter;
                break;
            case 'g':
                kind = SwiftSymbol.Kind.getter;
                break;
            case 'G':
                kind = SwiftSymbol.Kind.globalGetter;
                break;
            case 'w':
                kind = SwiftSymbol.Kind.willSet;
                break;
            case 'W':
                kind = SwiftSymbol.Kind.didSet;
                break;
            case 'r':
                kind = SwiftSymbol.Kind.readAccessor;
                break;
            case 'M':
                kind = SwiftSymbol.Kind.modifyAccessor;
                break;
            case 'a':
                switch (scanner.readChar()) {
                    case 'O':
                        kind = SwiftSymbol.Kind.owningMutableAddressor;
                        break;
                    case 'o':
                        kind = SwiftSymbol.Kind.nativeOwningMutableAddressor;
                        break;
                    case 'p':
                        kind = SwiftSymbol.Kind.nativePinningMutableAddressor;
                        break;
                    case 'u':
                        kind = SwiftSymbol.Kind.unsafeMutableAddressor;
                        break;
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
            case 'l':
                switch (scanner.readChar()) {
                    case 'O':
                        kind = SwiftSymbol.Kind.owningAddressor;
                        break;
                    case 'o':
                        kind = SwiftSymbol.Kind.nativeOwningAddressor;
                        break;
                    case 'p':
                        kind = SwiftSymbol.Kind.nativePinningAddressor;
                        break;
                    case 'u':
                        kind = SwiftSymbol.Kind.unsafeAddressor;
                        break;
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
            case 'p':
                return child;
            default: {
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                return null;
            }
        }
        return new SwiftSymbol(kind, child);
    }

    private SwiftSymbol demangleFunctionEntity() throws Exception {
        SwiftSymbol.DemangleFunctionEntityArgs args = SwiftSymbol.DemangleFunctionEntityArgs.none;
        SwiftSymbol.Kind kind = SwiftSymbol.Kind.type;

        switch (scanner.readChar()) {
            case 'D': {
                kind = SwiftSymbol.Kind.deallocator;
            }
            break;
            case 'd': {
                kind = SwiftSymbol.Kind.destructor;
            }
            break;
            case 'E': {
                kind = SwiftSymbol.Kind.iVarDestroyer;
            }
            break;
            case 'e': {
                kind = SwiftSymbol.Kind.iVarInitializer;
            }
            break;
            case 'i': {
                kind = SwiftSymbol.Kind.initializer;
            }
            break;
            case 'C': {
                kind = SwiftSymbol.Kind.allocator;
                args = SwiftSymbol.DemangleFunctionEntityArgs.typeAndMaybePrivateName;
            }
            break;

            case 'c': {
                kind = SwiftSymbol.Kind.constructor;
                args = SwiftSymbol.DemangleFunctionEntityArgs.typeAndMaybePrivateName;

            }
            break;
            case 'U': {
                kind = SwiftSymbol.Kind.explicitClosure;
                args = SwiftSymbol.DemangleFunctionEntityArgs.typeAndIndex;
            }
            break;
            case 'u': {
                kind = SwiftSymbol.Kind.implicitClosure;
                args = SwiftSymbol.DemangleFunctionEntityArgs.typeAndIndex;
            }
            break;
            case 'A': {
                kind = SwiftSymbol.Kind.defaultArgumentInitializer;
                args = SwiftSymbol.DemangleFunctionEntityArgs.index;
            }
            break;
            case 'p':
                return demangleEntity(SwiftSymbol.Kind.genericTypeParamDecl);
            default:
                scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
        }

        SafeArrayList children = new SafeArrayList();
        switch (args) {
            case none:
                break;
            case index:
                children.add(demangleIndexAsName());
                break;
            case typeAndIndex: {
                SwiftSymbol index = demangleIndexAsName();
                SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
                children.addAll(Utility.CreateLists(index, type));
            }
            break;
            case typeAndMaybePrivateName: {
                SwiftSymbol privateName = pop(SwiftSymbol.Kind.privateDeclName);
                SwiftSymbol paramType = require(pop(SwiftSymbol.Kind.type));
                SwiftSymbol labelList = popFunctionParamLabels(paramType);
                if (labelList != null) {
                    children.add(labelList);
                    children.add(paramType);
                } else {
                    children.add(paramType);
                }
                if (privateName != null) {
                    children.add(privateName);
                }
            }
        }
        List newList = Utility.CreateLists(popContext());
        newList.addAll(children);
        return new SwiftSymbol(kind, newList, null);
    }

    private SwiftSymbol demangleEntity(SwiftSymbol.Kind kind) throws Exception {
        SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
        SwiftSymbol name = require(pop(kind1 -> kind1.isDeclName()));
        SwiftSymbol context = popContext();
        return new SwiftSymbol(kind, Utility.CreateLists(context, name, type), null);
    }

    private SwiftSymbol demangleVariable() throws Exception {
        return demangleAccessor(demangleEntity(SwiftSymbol.Kind.variable));
    }

    private SwiftSymbol demangleSubscript() throws Exception {
        SwiftSymbol privateName = pop(SwiftSymbol.Kind.privateDeclName);
        SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
        SwiftSymbol labelList = require(popFunctionParamLabels(type));
        SwiftSymbol context = popContext();

        SwiftSymbol ss = new SwiftSymbol(SwiftSymbol.Kind.subscript, Utility.CreateLists(context, labelList, type), null);
        if (privateName != null) {
            ss.children.add(privateName);
        }
        return demangleAccessor(ss);
    }

    private SwiftSymbol demangleProtocolList() throws Exception {
        SwiftSymbol typeList = new SwiftSymbol(SwiftSymbol.Kind.typeList, (SwiftSymbol) null);
        if (pop(SwiftSymbol.Kind.emptyList) == null) {
            boolean firstElem = false;
            do {
                firstElem = pop(SwiftSymbol.Kind.firstElementMarker) != null;
                typeList.children.add(0, popProtocol());
            } while (!firstElem);
        }
        return new SwiftSymbol(SwiftSymbol.Kind.protocolList, typeList);
    }

    private SwiftSymbol demangleProtocolListType() throws Exception {
        return new SwiftSymbol(SwiftSymbol.Kind.type, demangleProtocolList());
    }

    private SwiftSymbol demangleGenericSignature(boolean hasParamCounts) throws Exception {
        SwiftSymbol sig = new SwiftSymbol(SwiftSymbol.Kind.dependentGenericSignature, (SwiftSymbol) null);
        if (hasParamCounts) {
            while (!scanner.conditional("l")) {
                int count = 0;
                if (!scanner.conditional("z")) {
                    count = demangleIndex() + 1;
                }
                sig.children.add(new SwiftSymbol(SwiftSymbol.Kind.dependentGenericParamCount, null, new Contents(count)));
            }
        } else {
            sig.children.add(new SwiftSymbol(SwiftSymbol.Kind.dependentGenericParamCount, null, new Contents(1)));
        }
        int requirementsIndex = sig.children.size();
        SwiftSymbol req = pop(kind -> kind.isRequrement());
        while (req != null) {
            sig.children.add(requirementsIndex, req);
            req = pop(kind -> kind.isRequrement());
        }
        return sig;
    }

    private SwiftSymbol demangleGenericRequirement() throws Exception {
        SwiftSymbol.DemangleGenericRequirementConstraintKind constraint;
        SwiftSymbol.DemangleGenericRequirementTypeKind type;
        switch (scanner.readChar()) {
            case 'c': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.baseClass;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.assoc;
            }
            break;
            case 'C': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.baseClass;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.compoundAssoc;
            }
            break;
            case 'b': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.baseClass;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.generic;
            }
            break;
            case 'B': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.baseClass;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.substitution;
            }
            break;
            case 't': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.sameType;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.assoc;
            }
            break;
            case 'T': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.sameType;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.compoundAssoc;
            }
            break;
            case 's': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.sameType;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.generic;
            }
            break;
            case 'S': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.sameType;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.substitution;
            }
            break;
            case 'm': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.layout;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.assoc;
            }
            break;
            case 'M': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.layout;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.compoundAssoc;
            }
            break;
            case 'l': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.layout;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.generic;
            }
            break;
            case 'L': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.layout;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.substitution;
            }
            break;
            case 'p': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.protocol;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.assoc;
            }
            break;
            case 'P': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.protocol;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.compoundAssoc;
            }
            break;
            case 'Q': {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.protocol;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.substitution;
            }
            break;
            default: {
                constraint = SwiftSymbol.DemangleGenericRequirementConstraintKind.protocol;
                type = SwiftSymbol.DemangleGenericRequirementTypeKind.generic;
                scanner.backtrack(1);
            }
        }

        SwiftSymbol constrType = null;
        switch (type) {
            case generic: {
                constrType = new SwiftSymbol(SwiftSymbol.Kind.type, demangleGenericParamIndex());
            }
            break;
            case assoc: {
                constrType = demangleAssociatedTypeSimple(demangleGenericParamIndex());
                substitutions.add(constrType);
            }
            break;
            case compoundAssoc: {
                constrType = demangleAssociatedTypeCompound(demangleGenericParamIndex());
                substitutions.add(constrType);
            }
            break;
            case substitution:
                constrType = require(pop(SwiftSymbol.Kind.type));
                break;
        }

        switch (constraint) {
            case protocol:
                return new SwiftSymbol(SwiftSymbol.Kind.dependentGenericConformanceRequirement, Utility.CreateLists(constrType, popProtocol()), null);
            case baseClass:
                return new SwiftSymbol(SwiftSymbol.Kind.dependentGenericConformanceRequirement, Utility.CreateLists(constrType, require(pop(SwiftSymbol.Kind.type))), null);
            case sameType:
                return new SwiftSymbol(SwiftSymbol.Kind.dependentGenericSameTypeRequirement, Utility.CreateLists(constrType, require(pop(SwiftSymbol.Kind.type))), null);
            case layout: {
                char c = scanner.readChar();
                SwiftSymbol size = null;
                SwiftSymbol alignment = null;
                switch (c) {
                    case 'U':
                    case 'R':
                    case 'N':
                    case 'C':
                    case 'D':
                    case 'T':
                        break;
                    case 'E':
                    case 'M': {
                        size = demangleIndexAsName();
                        alignment = demangleIndexAsName();
                    }
                    break;
                    case 'e':
                    case 'm':
                        size = demangleIndexAsName();
                        break;
                    default:
                        scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
                }
                SwiftSymbol name = new SwiftSymbol(SwiftSymbol.Kind.identifier, null, new Contents(String.valueOf(c)));
                SwiftSymbol layoutRequirement = new SwiftSymbol(SwiftSymbol.Kind.dependentGenericLayoutRequirement, Utility.CreateLists(constrType, name), null);
                if (size != null) {
                    layoutRequirement.children.add(size);
                }
                if (alignment != null) {
                    layoutRequirement.children.add(alignment);
                }
                return layoutRequirement;
            }
        }
        return null;
    }

    private SwiftSymbol demangleGenericType() throws Exception {
        SwiftSymbol genSig = require(pop(SwiftSymbol.Kind.dependentGenericSignature));
        SwiftSymbol type = require(pop(SwiftSymbol.Kind.type));
        return new SwiftSymbol(SwiftSymbol.Kind.dependentGenericType, Utility.CreateLists(genSig, type), null);
    }

    private SwiftSymbol demangleValueWitness() throws Exception {
        String code = scanner.readChars(2);
        SwiftSymbol.ValueWitnessKind kind = require(SwiftSymbol.ValueWitnessKind.WitnessKindWithString(code));
        return new SwiftSymbol(SwiftSymbol.Kind.valueWitness, Utility.CreateLists(require(pop(SwiftSymbol.Kind.type))), new Contents(kind.nCode));
    }

    private SwiftSymbol demangleMultiSubstitutions() throws Exception {
        int repeatCount = 0;
        while (true) {
            char c = scanner.readChar();
            if (Character.isLowerCase(c)) {
                SwiftSymbol ss = pushMultiSubstitutions(repeatCount, (c - 'a'));
                nameStack.add(ss);
                repeatCount = 0;
                continue;
            } else if (Character.isUpperCase(c)) {
                return pushMultiSubstitutions(repeatCount, (c - 'A'));
            } else if (c == '_') {
                return require(substitutions.get(27 + repeatCount));
            } else {
                scanner.backtrack(1);
                int dn = demangleNatural();
                repeatCount = dn > 0 ? dn : 0;
            }
        }
    }

    /*
     * OC mangle type
     */
    private SwiftSymbol demangleObjCTypeName() throws Exception {
        SwiftSymbol type = new SwiftSymbol(SwiftSymbol.Kind.type, (SwiftSymbol) null);
        if (scanner.conditional("C")) {
            SwiftSymbol module = null;
            if (scanner.conditional("s")) {
                module = new SwiftSymbol(SwiftSymbol.Kind.module, new SafeArrayList<>(), new Contents(stdlibName));
            } else {
                module = demangleIdentifier().changeKind(SwiftSymbol.Kind.module, new SafeArrayList());
            }
            type.children.add(new SwiftSymbol(SwiftSymbol.Kind.theClass, Utility.CreateLists(module, demangleIdentifier()), null));
        } else if (scanner.conditional("P")) {
            SwiftSymbol module = null;
            if (scanner.conditional("s")) {
                module = new SwiftSymbol(SwiftSymbol.Kind.module, null, new Contents(stdlibName));
            } else {
                module = demangleIdentifier().changeKind(SwiftSymbol.Kind.module, null);
            }
            type.children.add(new SwiftSymbol(SwiftSymbol.Kind.protocolList, new SwiftSymbol(SwiftSymbol.Kind.typeList, new SwiftSymbol(SwiftSymbol.Kind.type, new SwiftSymbol(SwiftSymbol.Kind.protocol, Utility.CreateLists(module, demangleIdentifier()), null)))));
            scanner.match("_");
        } else {
            scanner.throwException(SymbolScanner.MangledExceptionType.matchFailed);
        }
        require(scanner.isAtEnd());
        return new SwiftSymbol(SwiftSymbol.Kind.global, new SwiftSymbol(SwiftSymbol.Kind.typeMangling, type));
    }
//TODO:Punycode?
    private String decodeSwiftPunycode(String value) {
        String input = value;
        String output = "";

        int pos = 0;
        // Unlike RFC3492, Swift uses underscore for delimiting
        int ipos = input.indexOf('_');
        if (ipos > 0) {
            output += input.substring(0, ipos);
            pos = ipos + 1;
        }

        // Magic numbers from RFC3492
        int n = 128;
        int i = 0;
        int bias = 72;
        int symbolCount = 36;
        int alphaCount = 26;

        while (pos != (input.length())) {
            int oldi = i;
            int w = 1;
            for (int j = symbolCount; j < Integer.MAX_VALUE; j += symbolCount) {
                // Unlike RFC3492, Swift uses letters A-J for values 26-35
                char[] inputChars = input.toCharArray();
                int digit = inputChars[pos] >= 'a' ? (inputChars[pos] - 'a') : (inputChars[pos] - 'A' + alphaCount);
                if (pos != input.length()) {
                    pos++;
                }
                i += (digit * w);
                int t = Math.max(Math.min((j - bias), alphaCount), 1);
                if (digit < t) {
                    break;
                }
                w *= (symbolCount - t);
            }

            // Bias adaptation function
            float delta = (i - oldi) / ((oldi == 0) ? 700 : 2);
            delta = delta + delta / (output.length() + 1);
            int k = 0;
            while (delta > 455) {
                delta = delta / (symbolCount - 1);
                k = k + symbolCount;
            }
            k += (symbolCount * delta) / (delta + symbolCount + 2);

            bias = k;
            n = n + i / (output.length() + 1);
            i = i % (output.length() + 1);
            StringBuilder sb = new StringBuilder(output);
            sb.insert(i, (char)n);
            i += 1;
            output = sb.toString();
        }
        return output;
    }

}
