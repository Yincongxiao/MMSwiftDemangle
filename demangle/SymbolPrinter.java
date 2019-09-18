/*
 * Created by Yin Congxiao.
 */

package demangle;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Scanner;

public class SymbolPrinter {
    //SymbolPrintOptions
    static final int SYNTHESIZESUGARONTYPES = 1 << 0;
    static final int DISPLAYDEBUGGERGENERATEDMODULE = 1 << 1;
    static final int QUALIFYENTITIES = 1 << 2;
    static final int DISPLAYEXTENSIONCONTEXTS = 1 << 3;
    static final int DISPLAYUNMANGLEDSUFFIX = 1 << 4;
    static final int DISPLAYMODULENAMES = 1 << 5;
    static final int DISPLAYGENERICSPECIALIZATIONS = 1 << 6;
    static final int DISPLAYPROTOCOLCONFORMANCES = 1 << 5;
    static final int DISPLAYWHERECLAUSES = 1 << 8;
    static final int DISPLAYENTITYTYPES = 1 << 9;
    static final int SHORTENPARTIALAPPLY = 1 << 10;
    static final int SHORTENTHUNK = 1 << 11;
    static final int SHORTENVALUEWITNESS = 1 << 12;
    static final int SHORTENARCHETYPE = 1 << 13;
    static final int SHOWPRIVATEDISCRIMINATORS = 1 << 14;
    static final int SHOWFUNCTIONARGUMENTTYPES = 1 << 15;

    static final int defaultOptions = DISPLAYDEBUGGERGENERATEDMODULE | QUALIFYENTITIES | DISPLAYEXTENSIONCONTEXTS | DISPLAYUNMANGLEDSUFFIX | DISPLAYMODULENAMES | DISPLAYGENERICSPECIALIZATIONS | DISPLAYPROTOCOLCONFORMANCES | DISPLAYWHERECLAUSES | DISPLAYENTITYTYPES | SHOWPRIVATEDISCRIMINATORS | SHOWFUNCTIONARGUMENTTYPES;
    static final int simplified = SYNTHESIZESUGARONTYPES | QUALIFYENTITIES | SHORTENPARTIALAPPLY | SHORTENTHUNK | SHORTENVALUEWITNESS | SHORTENARCHETYPE;

    @IntDef({SYNTHESIZESUGARONTYPES, DISPLAYDEBUGGERGENERATEDMODULE, QUALIFYENTITIES, DISPLAYEXTENSIONCONTEXTS, DISPLAYUNMANGLEDSUFFIX, DISPLAYMODULENAMES, DISPLAYGENERICSPECIALIZATIONS, DISPLAYPROTOCOLCONFORMANCES, DISPLAYWHERECLAUSES, DISPLAYENTITYTYPES, SHORTENPARTIALAPPLY, SHORTENTHUNK, SHORTENVALUEWITNESS, SHORTENARCHETYPE, SHOWPRIVATEDISCRIMINATORS, SHOWFUNCTIONARGUMENTTYPES})
    @Retention(RetentionPolicy.SOURCE)
    @interface SymbolPrintOptions {
    }

    interface Mapable {
        <T> void map(T i);
    }

    public enum SugarType {
        none,
        optional,
        implicitlyUnwrappedOptional,
        array,
        dictionary
    }

    public enum TypePrinting {
        noType,
        withColon,
        functionStyle
    }

    private @SymbolPrintOptions
    int options;
    String target;
    boolean specializationPrefixPrinted;

    public SymbolPrinter(@SymbolPrintOptions int options) {
        this.options = options;
        this.target = "";
        this.specializationPrefixPrinted = false;
        int i = SYNTHESIZESUGARONTYPES;
    }

    public SwiftSymbol printOptional(SwiftSymbol optional, String prefix, String suffix, boolean asPrefixContext) {
        if (optional == null) return null;
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        target += prefix.isEmpty() ? "" : prefix;
        SwiftSymbol r = printName(optional, false);
        target += suffix.isEmpty() ? "" : suffix;
        return r;
    }

    public void printFirstChild(SwiftSymbol ofName, String prefix, String suffix, boolean asPrefixContext) {
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        printOptional(ofName.children.get(0), prefix, suffix, false);
    }

    public void printSequence(List<SwiftSymbol> names, String prefix, String suffix, String separator) {
        for (int i = 0; i < names.size(); i++) {
            Object obj = names.get(i);
            if (!obj.getClass().equals(SwiftSymbol.class)) {
                return;
            }
        }

        boolean isFirst = true;
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
//        if (separator == null) separator = "";
        target += prefix;
        for (SwiftSymbol c : names) {
            if (separator != null && !isFirst) {
                target += separator;
            } else {
                isFirst = false;
            }
            printName(c, false);
        }
        target += suffix;
    }

    public void printChildren(SwiftSymbol ofName, String prefix, String suffix, String separator) {
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        if (separator == null) separator = "";
        printSequence(ofName.children, prefix, suffix, separator);
    }

    private <T> void map(T int_, Mapable callback) {
        if (int_ != null) {
            callback.map(int_);
        }
    }

    public SwiftSymbol printName(SwiftSymbol name, boolean asPrefixContext) {
        switch (name.kind) {
            case theStatic:
                printFirstChild(name, "static ", null, false);
                break;
            case curryThunk:
                printFirstChild(name, "curry thunk of ", null, false);
                break;
            case dispatchThunk:
                printFirstChild(name, "dispatch thunk of ", null, false);
                break;
            case methodDescriptor:
                printFirstChild(name, "method descriptor for ", null, false);
                break;
            case methodLookupFunction:
                printFirstChild(name, "method lookup function for ", null, false);
                break;
            case outlinedBridgedMethod:
                target += "outlined bridged method (" + (name.text().isEmpty() ? "" : name.text()) + ") of ";
                break;
            case outlinedCopy:
                printFirstChild(name, "outlined copy of ", null, false);
                break;
            case outlinedConsume:
                printFirstChild(name, "outlined consume of ", null, false);
                break;
            case outlinedRetain:
                printFirstChild(name, "outlined retain of ", null, false);
                break;
            case outlinedRelease:
                printFirstChild(name, "outlined release of ", null, false);
                break;
            case outlinedInitializeWithTake:
                printFirstChild(name, "outlined init with take of ", null, false);
                break;
            case outlinedInitializeWithCopy:
                printFirstChild(name, "outlined init with copy of ", null, false);
                break;
            case outlinedAssignWithTake:
                printFirstChild(name, "outlined assign with take of ", null, false);
                break;
            case outlinedAssignWithCopy: {
                int index = name.children.get(0).index();
                target += "outlined variable #";
                target += index;
                target += " of ";
            }
            break;
            case outlinedDestroy:
                target += "outlined destroy of ";
                break;
            case outlinedVariable:
                target += ("outlined variable #" + (name.index() > 0 ? name.index() : 0) + " of ");
                break;
            case directness: {
                SwiftSymbol.Directness dir = SwiftSymbol.Directness.DirectnessWithValue(name.index());
                if (dir != null) {
                    target += dir.description();
                }
            }
            break;
            case anonymousContext: {
                if ((options & QUALIFYENTITIES) == QUALIFYENTITIES && (options & DISPLAYEXTENSIONCONTEXTS) == DISPLAYEXTENSIONCONTEXTS) {
                    printOptional(name.children.get(1), null, null, false);
                    String text = "";
                    if (name.children.get(0).text() != null) {
                        text = name.children.get(0).text();
                    }
                    target += ".(unknown context at " + text + ")";
                    SwiftSymbol second = name.children.get(2);
                    if (second != null && !second.children.isEmpty()) {
                        target += "<";
                        printName(second, false);
                        target += ">";
                    }
                }
            }
            break;
            case extension: {
                if ((options & QUALIFYENTITIES) == QUALIFYENTITIES && (options & DISPLAYEXTENSIONCONTEXTS) == DISPLAYEXTENSIONCONTEXTS) {
                    printFirstChild(name, "(extension in ", "):", true);
                }
                printSequence(Utility.slice(name.children, 1, 3), null, null, null);
            }
            break;
            case variable:
                return printEntity(name, asPrefixContext, TypePrinting.withColon, true, null, 0, null);
            case function:
            case boundGenericFunction:
                return printEntity(name, asPrefixContext, TypePrinting.functionStyle, true, null, 0, null);
            case subscript:
                return printEntity(name, asPrefixContext, TypePrinting.functionStyle, true, null, 0, "subscript");
            case genericTypeParamDecl:
            case theClass:
            case structure:
            case theEnum:
            case protocol:
            case typeAlias:
            case otherNominalType:
                return printEntity(name, asPrefixContext, TypePrinting.noType, true, null, 0, null);
            case explicitClosure:
                return printEntity(name, asPrefixContext, ((options & SHOWFUNCTIONARGUMENTTYPES) == SHOWFUNCTIONARGUMENTTYPES) ? TypePrinting.functionStyle : TypePrinting.noType, false, "closure #", (name.children.get(1).index()) + 1, null);
            case implicitClosure:
                return printEntity(name, asPrefixContext, ((options & SHOWFUNCTIONARGUMENTTYPES) == SHOWFUNCTIONARGUMENTTYPES) ? TypePrinting.functionStyle : TypePrinting.noType, false, "implicit closure #", (name.children.get(1).index()) + 1, null);
            case global:
                printChildren(name, null, null, null);
                break;
            case suffix: {
                if ((options & DISPLAYUNMANGLEDSUFFIX) == DISPLAYUNMANGLEDSUFFIX) {
                    target += " with unmangled suffix ";
                    quotedString(name.text().isEmpty() ? "" : name.text());
                }
            }
            break;
            case initializer:
                return printEntity(name, asPrefixContext, TypePrinting.noType, false, "variable initialization expression", 0, null);
            case defaultArgumentInitializer:
                return printEntity(name, asPrefixContext, TypePrinting.noType, false, ("default argument " + String.valueOf(name.children.get(1).index())), 0, null);
            case declContext:
            case type:
            case typeMangling:
                printFirstChild(name, "", "", false);
                break;
            case localDeclName:
                printOptional(name.children.get(1), null, (" #" + ((name.children.get(0).index()) + 1)), false);
                break;
            case privateDeclName: {
                printOptional(name.children.get(1), (options & SHOWPRIVATEDISCRIMINATORS) == SHOWPRIVATEDISCRIMINATORS ? "(" : "", "", false);
                String t_i = name.children.size() > 1 ? " " : "(";
                String t_j = !name.children.get(0).text().isEmpty() ? name.children.get(0).text() : "";
                target += (options & SHOWPRIVATEDISCRIMINATORS) == SHOWPRIVATEDISCRIMINATORS ? (t_i + "in " + t_j + ")") : "";
            }
            break;
            case relatedEntityDeclName:
                printFirstChild(name, ("related decl '" + (!name.text().isEmpty() ? name.text() : "") + "' for "), "", false);
                break;
            case module:
                if ((options & DISPLAYMODULENAMES) == DISPLAYMODULENAMES) {
                    target += (!name.text().isEmpty() ? name.text() : "");
                }
                break;
            case identifier:
                target += (!name.text().isEmpty() ? name.text() : "");
                break;
            case index:
                target += (name.index() < 0) ? 0 : name.index();
                break;
            case noEscapeFunctionType:
            case functionType:
            case uncurriedFunctionType:
                printFunctionType(null, name);
                break;
            case escapingAutoClosureType:
            case autoClosureType: {
                target += "@autoclosure ";
                printFunctionType(null, name);
            }
            break;
            case thinFunctionType: {
                target += "@convention(thin) ";
                printFunctionType(null, name);
            }
            break;
            case argumentTuple: {
                printFunctionParameters(null, name, (options & SHOWFUNCTIONARGUMENTTYPES) == SHOWFUNCTIONARGUMENTTYPES);
            }
            break;
            case tuple:
                printChildren(name, "(", ")", ", ");
                break;
            case tupleElement: {
                SwiftSymbol first = Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.tupleElementName);
                if (first != null) {
                    target += (!first.text().isEmpty() ? first.text() : "") + ": ";
                }

                SwiftSymbol type = Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.type);
                if (type == null) {
                    break;
                }
                printName(type, false);
                SwiftSymbol last = Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.variadicMarker);
                if (last != null) {
                    target += "...";
                }

            }
            break;
            case tupleElementName:
                target += (!name.text().isEmpty() ? name.text() : "") + ": ";
                break;
            case returnType: {
                target += " -> ";
                if (name.children.isEmpty() && !name.text().isEmpty()) {
                    target += name.text();
                } else {
                    printChildren(name, null, null, null);
                }
            }
            break;
            case retroactiveConformance: {
                if (name.children.size() == 2) {
                    printChildren(name, "retroactive @ ", null, null);
                }
            }
            break;
            case weak:
                printFirstChild(name, "weak ", "", false);
                break;
            case unowned:
                printFirstChild(name, "unowned ", "", false);
                break;
            case unmanaged:
                printFirstChild(name, "unowned(unsafe) ", "", false);
                break;
            case inOut:
                printFirstChild(name, "inout ", "", false);
                break;
            case shared:
                printFirstChild(name, "__shared ", "", false);
                break;
            case owned:
                printFirstChild(name, "__owned ", "", false);
                break;
            case nonObjCAttribute:
                target += "@nonobjc ";
                break;
            case objCAttribute:
                target += "@objc ";
                break;
            case directMethodReferenceAttribute:
                target += "super ";
                break;
            case dynamicAttribute:
                target += "dynamic ";
                break;
            case vTableAttribute:
                target += "override ";
            case functionSignatureSpecialization:
                printSpecializationPrefix(name, "function signature specialization", "");
                break;
            case genericPartialSpecialization:
                printSpecializationPrefix(name, "generic partial specialization", "Signature = ");
                break;
            case genericPartialSpecializationNotReAbstracted:
                printSpecializationPrefix(name, "generic not re-abstracted partial specialization", "Signature = ");
            case genericSpecialization:
                printSpecializationPrefix(name, "generic specialization", "");
                break;
            case genericSpecializationNotReAbstracted:
                printSpecializationPrefix(name, "generic not re-abstracted specialization", "");
                break;
            case specializationIsFragile:
                target += "preserving fragile attribute";
                break;
            case genericSpecializationParam: {
                printFirstChild(name, "", "", false);
                printOptional(name.children.get(1), " with ", "", false);
                List<SwiftSymbol> subList = Utility.slice(name.children, 2, name.children.size());
                for (SwiftSymbol s : subList) {
                    target += " and ";
                    printName(s, false);
                }
            }
            break;
            case functionSignatureSpecializationParam: {
                int index = name.index() > 0 ? name.index() : 0;
                target += "Arg[" + index + "] = ";
                int idx = printFunctionSigSpecializationParam(name, 0);
                while (idx < name.children.size()) {
                    target += " and ";
                    idx = printFunctionSigSpecializationParam(name, idx);
                }
            }
            break;
            case functionSignatureSpecializationParamPayload:
                try {
                    String desc = MMSwiftDemangle.parsedMangledSwiftSymbol(name.text()).description();
                    target += (desc.isEmpty() ? "" : desc);
                } catch (Exception exception) {

                }
                break;
            case functionSignatureSpecializationParamKind: {
                int raw = name.index() > 0 ? name.index() : 0;
                SwiftSymbol.FunctionSigSpecializationParamKind kind = SwiftSymbol.FunctionSigSpecializationParamKind.paramKind(raw);
                if (kind != null) {
                    target += kind.description();
                } else {
                    SafeArrayList<SwiftSymbol.FunctionSigSpecializationParamKind> kinds = new SafeArrayList();
                    kinds.add(SwiftSymbol.FunctionSigSpecializationParamKind.existentialToGeneric);
                    kinds.add(SwiftSymbol.FunctionSigSpecializationParamKind.dead);
                    kinds.add(SwiftSymbol.FunctionSigSpecializationParamKind.ownedToGuaranteed);
                    kinds.add(SwiftSymbol.FunctionSigSpecializationParamKind.guaranteedToOwned);
                    kinds.add(SwiftSymbol.FunctionSigSpecializationParamKind.sroa);
                    String sep = " and ";

                    SafeArrayList<String> newList = new SafeArrayList();
                    for (SwiftSymbol.FunctionSigSpecializationParamKind kind_ : kinds) {
                        if ((raw & kind_.getValue()) == kind_.getValue()) {
                            if (!kind_.description().isEmpty()) newList.add(kind_.description());
                        }
                    }
                    for (int i = 0; i < newList.size(); i++) {
                        target += newList.get(i);
                        if (i < newList.size() - 1) {
                            target += sep;
                        }
                    }
                }
            }
            break;
            case specializationPassID:
            case number: {
                target += name.index() > 0 ? name.index() : 0;
            }
            break;
            case builtinTypeName: {
                target += !name.text().isEmpty() ? name.text() : "";
            }
            break;
            case infixOperator: {
                target += !name.text().isEmpty() ? name.text() : "";
                target += " infix";
            }
            break;
            case prefixOperator: {
                target += !name.text().isEmpty() ? name.text() : "";
                target += " prefix";
            }
            break;
            case postfixOperator: {
                target += !name.text().isEmpty() ? name.text() : "";
                target += " postfix";
            }
            break;
            case lazyProtocolWitnessTableAccessor: {
                printOptional(name.children.get(0), "lazy protocol witness table accessor for type ", null, false);
                printOptional(name.children.get(1), " and conformance ", null, false);
            }
            break;
            case lazyProtocolWitnessTableCacheVariable: {
                printOptional(name.children.get(0), "lazy protocol witness table cache variable for type ", null, false);
                printOptional(name.children.get(1), " and conformance ", "", false);
            }
            break;
            case protocolWitnessTableAccessor:
                printFirstChild(name, "protocol witness table accessor for ", "", false);
                break;
            case protocolWitnessTable:
                printFirstChild(name, "protocol witness table for ", "", false);
                break;
            case protocolWitnessTablePattern:
                printFirstChild(name, "protocol witness table pattern for ", "", false);
                break;
            case genericProtocolWitnessTable:
                printFirstChild(name, "generic protocol witness table for ", "", false);
                break;
            case genericProtocolWitnessTableInstantiationFunction:
                printFirstChild(name, "instantiation function for generic protocol witness table for ", "", false);
                break;
            case resilientProtocolWitnessTable:
                target += "resilient protocol witness table for ";
                printFirstChild(name, "", "", false);
                break;
            case vTableThunk: {
                printOptional(name.children.get(1), "vtable thunk for ", "", false);
                printOptional(name.children.get(0), " dispatching to ", "", false);
            }
            break;
            case protocolWitness: {
                printOptional(name.children.get(1), "protocol witness for ", "", false);
                printOptional(name.children.get(0), " in conformance ", "", false);
            }
            break;
            case partialApplyForwarder: {
                String text = (options & SHORTENPARTIALAPPLY) == SHORTENPARTIALAPPLY ? "" : " forwarder";
                target += "partial apply" + text;
                printFirstChild(name, " for ", "", false);
            }
            break;
            case partialApplyObjCForwarder: {
                String text = (options & SHORTENPARTIALAPPLY) == SHORTENPARTIALAPPLY ? "" : " ObjC forwarder";
                target += "partial apply" + text;
                printFirstChild(name, " for ", "", false);
            }
            break;
            case keyPathGetterThunkHelper: {
                printFirstChild(name, "key path getter for ", " : ", false);
                printOptional(name.children.get(1), "", "", false);
                printOptional(name.children.get(2), "", "", false);
            }
            break;
            case keyPathSetterThunkHelper: {
                printFirstChild(name, "key path setter for ", " : ", false);
                printOptional(name.children.get(1), "", "", false);
                printOptional(name.children.get(2), "", "", false);
            }
            break;
            case keyPathEqualsThunkHelper:
            case keyPathHashThunkHelper: {
                String text = name.kind == SwiftSymbol.Kind.keyPathEqualsThunkHelper ? "equality" : "hash";
                target += "key path index " + text + " operator for ";
                boolean dropLast = false;
                SwiftSymbol last = name.children.get(name.children.size() - 1);
                if (last != null && last.kind == SwiftSymbol.Kind.dependentGenericSignature) {
                    printName(last, false);
                    dropLast = true;
                }
                List newList = name.children;
                if (dropLast) {
                    newList = Utility.dropLast(name.children);
                }
                printSequence(newList, "(", ")", ", ");
            }
            break;
            case fieldOffset: {
                printFirstChild(name, "", "", false);
                printOptional(name.children.get(1), "field offset for ", "", true);
            }
            break;
            case enumCase: {
                target += "enum case for ";
                printFirstChild(name, "", " : ", false);
            }
            break;
            case reabstractionThunk:
            case reabstractionThunkHelper: {
                if ((options & SHORTENTHUNK) == SHORTENTHUNK) {
                    printOptional(name.children.get(name.children.size() - 2), "thunk for ", null, false);
                    break;
                }
                target += "reabstraction thunk ";
                target += name.kind == SwiftSymbol.Kind.reabstractionThunkHelper ? "helper " : "";
                SwiftSymbol first = Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.dependentGenericSignature);
                if (first != null) {
                    printOptional(first, "", " ", false);
                }
                printOptional(name.children.get(name.children.size() - 2), "from ", "", false);
                printOptional(name.children.get(name.children.size() - 1), " to ", "", false);
            }
            break;
            case mergedFunction:
                target += !((options & SHORTENTHUNK) == SHORTENTHUNK) ? "merged " : "";
                break;
            case symbolicReference:
                target += "symbolic reference " + name.index();
                break;
            case unresolvedSymbolicReference: {
                target += name.index();
            }
            break;
            case genericTypeMetadataPattern:
                printFirstChild(name, "generic type metadata pattern for ", "", false);
                break;
            case metaclass:
                printFirstChild(name, "metaclass for ", "", false);
                break;
            case protocolConformanceDescriptor:
                printFirstChild(name, "protocol conformance descriptor for ", "", false);
                break;
            case protocolDescriptor:
                printFirstChild(name, "protocol descriptor for ", "", false);
                break;
            case protocolRequirementsBaseDescriptor:
                printFirstChild(name, "protocol requirements base descriptor for ", "", false);
                break;
            case fullTypeMetadata:
                printFirstChild(name, "full type metadata for ", "", false);
                break;
            case typeMetadata:
                printFirstChild(name, "type metadata for ", "", false);
                break;
            case typeMetadataAccessFunction:
                printFirstChild(name, "type metadata accessor for ", "", false);
                break;
            case typeMetadataInstantiationCache:
                printFirstChild(name, "type metadata instantiation cache for ", "", false);
                break;
            case typeMetadataInstantiationFunction:
                printFirstChild(name, "type metadata instantiation cache for ", "", false);
                break;
            case typeMetadataSingletonInitializationCache:
                printFirstChild(name, "type metadata singleton initialization cache for ", "", false);
                break;
            case typeMetadataCompletionFunction:
                printFirstChild(name, "type metadata completion function for ", "", false);
                break;
            case typeMetadataLazyCache:
                printFirstChild(name, "lazy cache variable for type metadata for ", "", false);
                break;
            case associatedConformanceDescriptor: {
                printOptional(name.children.get(0), "associated conformance descriptor for ", "", false);
                printOptional(name.children.get(1), ".", "", false);
                printOptional(name.children.get(2), ": ", "", false);
            }
            break;
            case defaultAssociatedConformanceAccessor: {
                printOptional(name.children.get(0), "default associated conformance descriptor for ", "", false);
                printOptional(name.children.get(1), ".", "", false);
                printOptional(name.children.get(2), ": ", "", false);
            }
            break;
            case associatedTypeDescriptor:
                printFirstChild(name, "associated type descriptor for ", "", false);
                break;
            case associatedTypeMetadataAccessor: {
                printOptional(name.children.get(1), "associated type metadata accessor for ", "", false);
                printOptional(name.children.get(0), " in ", "", false);
            }
            break;
            case defaultAssociatedTypeMetadataAccessor:
                printFirstChild(name, "default associated type metadata accessor for ", "", false);
                break;
            case associatedTypeWitnessTableAccessor: {
                printOptional(name.children.get(1), "associated type witness table accessor for ", "", false);
                printOptional(name.children.get(2), " : ", "", false);
                printOptional(name.children.get(0), " in ", "", false);
            }
            break;
            case classMetadataBaseOffset:
                printFirstChild(name, "class metadata base offset for ", "", false);
                break;
            case propertyDescriptor:
                printFirstChild(name, "property descriptor for ", "", false);
                break;
            case nominalTypeDescriptor:
                printFirstChild(name, "nominal type descriptor for ", "", false);
                break;
            case coroutineContinuationPrototype:
                printFirstChild(name, "coroutine continuation prototype for ", "", false);
                break;
            case valueWitness: {
                target += SwiftSymbol.ValueWitnessKind.WitnessKindWithString(name.index()).toString();
                target += (options & SHORTENVALUEWITNESS) == SHORTENVALUEWITNESS ? " for " : " value witness for ";
                printFirstChild(name, "", "", false);
            }
            break;
            case valueWitnessTable:
                printFirstChild(name, "value witness table for ", "", false);
                break;
            case boundGenericClass:
            case boundGenericStructure:
            case boundGenericEnum:
            case boundGenericProtocol:
            case boundGenericOtherNominalType:
            case boundGenericTypeAlias:
                printBoundGeneric(name);
                break;
            case dynamicSelf:
                target += "Self";
                break;
            case cFunctionPointer:
                target += "@convention(c) ";
                printFunctionType(null, name);
                break;
            case objCBlock:
                target += "@convention(block) ";
                printFunctionType(null, name);
                break;
            case silBoxType: {
                target += "@box ";
                printFirstChild(name, "", "", false);
            }
            break;
            case metatype: {
                if (name.children.size() == 2) {
                    printFirstChild(name, " ", "", false);
                }
                SwiftSymbol first = name.children.get(name.children.size() == 2 ? 1 : 0).children.get(0);
                if (first == null) {
                    return null;
                }
                boolean needParens = !first.kind.isSimpleType();
                target += needParens ? "(" : "";
                printName(first, false);
                target += needParens ? ")" : "";
                target += first.kind.isExistentialType() ? ".Protocol" : ".Type";
            }
            break;
            case existentialMetatype: {
                if (name.children.size() == 2) {
                    printFirstChild(name, " ", "", false);
                }
                printOptional(name.children.get(name.children.size() == 2 ? 1 : 0), null, ".Type", false);

            }
            break;
            case metatypeRepresentation:
                target += name.text().isEmpty() ? "" : name.text();
                break;
            case associatedTypeRef: {
                printFirstChild(name, " ", "", false);
                String text = name.children.get(1) != null ? name.children.get(1).text() : "";
                target += "." + text;
            }
            break;
            case protocolList: {
                SwiftSymbol typeList = name.children.get(0);
                if (typeList == null) {
                    return null;
                }
                if (typeList.children.isEmpty()) {
                    target += "Any";
                } else {
                    printChildren(typeList, "", "", " & ");
                }
            }
            break;
            case protocolListWithClass: {
                if (name.children.size() < 2) return null;
                printOptional(name.children.get(1), null, " & ", false);
                SwiftSymbol protocolsTypeList = name.children.get(0).children.get(0);
                if (protocolsTypeList != null) {
                    printChildren(protocolsTypeList, null, "", " & ");
                }
            }
            break;
            case protocolListWithAnyObject: {
                SwiftSymbol prot = name.children.get(0);
                if (prot == null) return null;
                SwiftSymbol protocolsTypeList = prot.children.get(0);
                if (protocolsTypeList == null) return null;
                if (protocolsTypeList.children.size() > 0) {
                    printChildren(protocolsTypeList, "", " & ", " & ");
                }
                if ((options & QUALIFYENTITIES) == QUALIFYENTITIES) {
                    target += "Swift.";
                }
                target += "AnyObject";
            }
            break;
            case associatedType:
                return null;
            case owningAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "owningAddressor");
            case owningMutableAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "owningMutableAddressor");
            case nativeOwningAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "nativeOwningAddressor");
            case nativeOwningMutableAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "nativeOwningMutableAddressor");
            case nativePinningAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "nativePinningAddressor");
            case nativePinningMutableAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "nativePinningMutableAddressor");
            case unsafeAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "unsafeAddressor");
            case unsafeMutableAddressor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "unsafeMutableAddressor");
            case globalGetter:
            case getter:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "getter");
            case setter:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "setter");
            case materializeForSet:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "materializeForSet");
            case willSet:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "willset");
            case didSet:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "didset");
            case readAccessor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "read");
            case modifyAccessor:
                return printAbstractStorage(name.children.get(0), asPrefixContext,
                        "modify");
            case allocator:
                return printEntity(name, asPrefixContext, TypePrinting.functionStyle,
                        false, (name.children.get(0).kind == SwiftSymbol.Kind.theClass) ? "__allocating_init" : "init", 0, null);
            case constructor:
                return printEntity(name, asPrefixContext, TypePrinting.functionStyle,
                        name.children.size() > 2, "init", 0, null);
            case destructor:
                return printEntity(name, asPrefixContext, TypePrinting.noType,
                        false, "deinit", 0, null);
            case deallocator:
                return printEntity(name, asPrefixContext, TypePrinting.noType,
                        false, (name.children.get(0).kind == SwiftSymbol.Kind.theClass) ? "__deallocating_deinit" : "deinit", 0, null);
            case iVarInitializer:
                return printEntity(name, asPrefixContext, TypePrinting.noType,
                        false, "__ivar_initializer", 0, null);
            case iVarDestroyer:
                return printEntity(name, asPrefixContext, TypePrinting.noType,
                        false, "__ivar_destroyer", 0, null);
            case protocolConformance: {
                if (name.children.size() == 4) {
                    printOptional(name.children.get(2), "property behavior storage of ", "", false);
                    printOptional(name.children.get(0), " in ", "", false);
                    printOptional(name.children.get(1), " : ", "", false);
                } else {
                    printChildren(name, null, null, "");
                    if ((options & DISPLAYPROTOCOLCONFORMANCES) == DISPLAYPROTOCOLCONFORMANCES) {
                        printOptional(name.children.get(1), " : ", "", false);
                        printOptional(name.children.get(2), " in ", "", false);
                    }
                }
            }
            break;
            case typeList:
                printChildren(name, "", "", "");
                break;
            case labelList:
                break;
            case implEscaping:
                target += "@escaping";
                break;
            case implConvention:
            case implFunctionAttribute:
                target += name.text().isEmpty() ? "" : name.text();
                break;
            case implErrorResult:
                target += "@error";
                printChildren(name, "", "", " ");
                break;
            case implParameter:
            case implResult:
                printChildren(name, "", "", " ");
                break;
            case implFunctionType:
                printImplFunctionType(name);
                break;
            case errorType:
                target += "<ERROR TYPE>";
                break;
            case dependentPseudogenericSignature:
            case dependentGenericSignature: {
                target += "<";
                int lastDepth = 0;
                for (int i = 0; i < name.children.size(); i++) {
                    SwiftSymbol c = name.children.get(i);
                    if (c.kind != SwiftSymbol.Kind.dependentGenericParamCount) break;
                    lastDepth = i;
                    target += i == 0 ? "" : "><";
                    int count = c.index() > 0 ? c.index() : 0;
                    for (int j = 0; j < count; j++) {
                        target += (j != 0 ? ", " : "");
                        if (j >= 128) {
                            target += "...";
                            break;
                        }
                        target += archetypeName(j, i);
                    }
                }
                if (lastDepth != name.children.size() - 1) {
                    if ((options & DISPLAYWHERECLAUSES) == DISPLAYWHERECLAUSES) {
                        printSequence(Utility.slice(name.children, lastDepth + 1, name.children.size()), "where ", "", ", ");
                    }
                }
                target += ">";
            }
            break;
            case dependentGenericParamCount:
                return null;
            case dependentGenericConformanceRequirement: {
                printFirstChild(name, "", "", false);
                printOptional(name.children.get(1), ": ", "", false);
            }
            break;
            case dependentGenericLayoutRequirement: {
                SwiftSymbol layout = name.children.get(1);
                if (layout == null) return null;
                char c = '\u0000';
                if (!layout.text().isEmpty()) c = layout.text().charAt(0);
                if (c == '\u0000') return null;
                printFirstChild(name, "", ": ", false);

                switch (c) {
                    case 'U':
                        target += "_UnknownLayout";
                        break;
                    case 'R':
                        target += "_RefCountedObject";
                        break;
                    case 'N':
                        target += "_NativeRefCountedObject";
                        break;
                    case 'C':
                        target += "AnyObject";
                        break;
                    case 'D':
                        target += "_NativeClass";
                        break;
                    case 'T':
                    case 'E':
                    case 'e':
                        target += "_Trivial";
                        break;
                    case 'M':
                    case 'm':
                        target += "_TrivialAtMost";
                        break;
                    default:
                        break;
                }
                if (name.children.size() > 2) {
                    printOptional(name.children.get(2), "(", "", false);
                    printOptional(name.children.get(3), ", ", "", false);
                    target += ")";
                }
            }
            break;
            case dependentGenericSameTypeRequirement: {
                printFirstChild(name, "", "", false);
                printOptional(name.children.get(1), " == ", "", false);
            }
            break;
            case dependentGenericParamType:
                target += name.text().isEmpty() ? "" : name.text();
                break;
            case dependentGenericType: {
                SwiftSymbol depType = name.children.get(1);
                if (depType == null) {
                    return null;
                }
                printFirstChild(name, "", "", false);
                printOptional(depType, depType.needSpaceBeforeType() ? " " : "", "", false);
            }
            break;
            case dependentMemberType: {
                printFirstChild(name, "", "", false);
                target += ".";
                printOptional(name.children.get(1), "", "", false);
            }
            break;
            case dependentAssociatedTypeRef:
                target += (name.text().isEmpty() ? "" : name.text());
                break;
            case reflectionMetadataBuiltinDescriptor:
                printFirstChild(name, "reflection metadata builtin descriptor ", "", false);
                break;
            case reflectionMetadataFieldDescriptor:
                printFirstChild(name, "reflection metadata field descriptor ", "", false);
                break;
            case reflectionMetadataAssocTypeDescriptor:
                printFirstChild(name, "reflection metadata associated type descriptor ", "", false);
                break;
            case reflectionMetadataSuperclassDescriptor:
                printFirstChild(name, "reflection metadata superclass descriptor ", "", false);
                break;
            case throwsAnnotation:
                target += " throws ";
                break;
            case emptyList:
                target += " empty-list ";
                break;
            case firstElementMarker:
                target += " first-element-marker ";
                break;
            case variadicMarker:
                target += " variadic-marker ";
                break;
            case silBoxTypeWithLayout: {
                SwiftSymbol layout = name.children.get(0);
                if (layout == null) {
                    return null;
                }
                printOptional(name.children.get(1), "", " ", false);
                printName(layout, false);
                SwiftSymbol genericArgs = name.children.get(2);
                if (genericArgs != null) {
                    printSequence(genericArgs.children, " <", ">", ", ");
                }
            }
            break;
            case silBoxLayout:
                String text = name.children.isEmpty() ? "" : " ";
                printSequence(name.children, "{" + text, " }", ", ");
                break;
            case silBoxImmutableField:
            case silBoxMutableField:
                printFirstChild(name, name.kind == SwiftSymbol.Kind.silBoxImmutableField ? "let " : "var ", "", false);
                break;
            case assocTypePath:
                printChildren(name, "", "", ".");
                break;
            case moduleDescriptor:
                printChildren(name, "module descriptor ", "", "");
                break;
            case anonymousDescriptor:
                printChildren(name, "anonymous descriptor ", "", "");
                break;
            case extensionDescriptor:
                printChildren(name, "extension descriptor ", "", "");
                break;
            case associatedTypeGenericParamRef:
                printChildren(name, "generic parameter reference for associated type ", "", "");
                break;
        }
        return null;
    }

    public SwiftSymbol printAbstractStorage(SwiftSymbol name, boolean asPrefixContext, String extraName) {
        if (name == null) return null;
        switch (name.kind) {
            case variable:
                return printEntity(name, asPrefixContext, TypePrinting.withColon,
                        true, extraName, 0, extraName);
            case subscript:
                return printEntity(name, asPrefixContext, TypePrinting.withColon,
                        false, extraName, 0, "subscript");
            default:
                return null;
        }
    }

    public void printEntityType(SwiftSymbol name, SwiftSymbol type, SwiftSymbol genericFunctionTypeList) {
        SwiftSymbol labelList = demangle.Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.labelList);
        if (labelList != null || genericFunctionTypeList != null) {
            if (genericFunctionTypeList != null) {
                printChildren(genericFunctionTypeList, "<", ">", ", ");
            }
            SwiftSymbol t = type;
            if (type.kind == SwiftSymbol.Kind.dependentGenericType) {
                if (genericFunctionTypeList != null) {
                    printOptional(type.children.get(0), "", "", false);
                }
                SwiftSymbol dt = type.children.get(1);
                if (dt != null) {
                    if (dt.needSpaceBeforeType()) {
                        target += " ";
                    }
                    if (dt.children.get(0) != null) {
                        t = dt.children.get(0);
                    }
                }
            }
            printFunctionType(labelList, t);
        } else {
            printName(type, false);
        }
    }

    public SwiftSymbol printEntity(SwiftSymbol name, boolean asPrefixContext, TypePrinting typePrinting, boolean hasName, String extraName, int extraIndex, String overwriteName) {
        SwiftSymbol genericFunctionTypeList = null;
        SwiftSymbol first = name.children.get(0);
        SwiftSymbol second = name.children.get(1);
        if (name.kind == SwiftSymbol.Kind.boundGenericFunction && first != null && second != null) {
            name = first;
            genericFunctionTypeList = second;
        }
        boolean multiWordName = ((extraName != null) && extraName.contains(" ") == true) || (hasName && second.kind == SwiftSymbol.Kind.localDeclName);
        if (asPrefixContext && (typePrinting != TypePrinting.noType || multiWordName)) {
            return name;
        }

        SwiftSymbol context = name.children.get(0);
        if (context == null) return null;
        SwiftSymbol postfixContext = null;
        if (shouldPrintContext(context)) {
            if (multiWordName) {
                postfixContext = context;
            } else {
                int currentPos = target.length();
                postfixContext = printName(context, true);
                if (target.length() != currentPos) {
                    target += ".";
                }
            }
        }

        boolean extraNameConsumed = extraName == null;
        if (hasName || overwriteName != null) {
            if (!extraNameConsumed && multiWordName) {
                target += (extraName.isEmpty() ? "" : extraName) + " of ";
                extraNameConsumed = true;
            }
            int currentPos = target.length();
            if (overwriteName != null) {
                target += overwriteName;
            } else {
                SwiftSymbol one = name.children.get(1);
                if (one != null) {
                    if (one.kind != SwiftSymbol.Kind.privateDeclName) {
                        printName(one, false);
                    }
                    SwiftSymbol pdn = Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.privateDeclName);
                    if (pdn != null) {
                        printName(pdn, false);
                    }
                }
            }
            if (target.length() != currentPos && !extraNameConsumed) {
                target += ".";
            }
        }

        if (!extraNameConsumed) {
            target += extraName.isEmpty() ? "" : extraName;
            if (extraIndex > 0) {
                target += extraIndex;
            }
        }

        if (typePrinting != TypePrinting.noType) {
            SwiftSymbol type = Utility.First(name.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.type);
            if (type == null) {
                return null;
            }
            if (type.kind != SwiftSymbol.Kind.type) {
                SwiftSymbol nextType = name.children.get(2);
                if (nextType == null) return null;
                type = nextType;
            }
            if (type.kind != SwiftSymbol.Kind.type) return null;
            SwiftSymbol firstChild = type.children.get(0);
            if (firstChild == null) return null;
            type = firstChild;
            TypePrinting typePr = typePrinting;
            if (typePr == TypePrinting.functionStyle) {
                SwiftSymbol t = type;
                while (t.kind == SwiftSymbol.Kind.dependentGenericType) {
                    SwiftSymbol next = t.children.get(1).children.get(0);
                    if (next != null) {
                        t = next;
                    }
                }
                switch (t.kind) {
                    case functionType:
                    case uncurriedFunctionType:
                    case cFunctionPointer:
                    case thinFunctionType: {
                        break;
                    }
                    default:
                        typePr = TypePrinting.withColon;
                }
            }
            if (typePr == TypePrinting.withColon) {
                if ((options & DISPLAYENTITYTYPES) == DISPLAYENTITYTYPES) {
                    target += " : ";
                    printEntityType(name, type, genericFunctionTypeList);
                }
            } else {
                if (multiWordName || type.needSpaceBeforeType()) {
                    target += " ";
                }
                printEntityType(name, type, genericFunctionTypeList);
            }
        }

        if (!asPrefixContext && postfixContext != null) {
            if (name.kind == SwiftSymbol.Kind.defaultArgumentInitializer || name.kind == SwiftSymbol.Kind.initializer) {
                target += " of ";
            } else {
                target += " in ";
            }
            printName(postfixContext, false);
            return null;
        }
        return postfixContext;
    }

    public boolean shouldPrintContext(SwiftSymbol name) {
        if ((options & QUALIFYENTITIES) != QUALIFYENTITIES) {
            return false;
        }

        if (name.kind == SwiftSymbol.Kind.module && name.text().startsWith(Demangler.lldbExpressionsModuleNamePrefix) == true) {
            return (options & DISPLAYDEBUGGERGENERATEDMODULE) == DISPLAYDEBUGGERGENERATEDMODULE;
        }
        return true;
    }

    public int printFunctionSigSpecializationParam(SwiftSymbol name, int index) {
        SwiftSymbol firstChild = name.children.get(index);
        if (firstChild == null) return index + 1;
        int v = firstChild.index();
        if (v <= 0) {
            return index + 1;
        }

        SwiftSymbol.FunctionSigSpecializationParamKind kind = SwiftSymbol.FunctionSigSpecializationParamKind.paramKind(v);
        switch (kind) {
            case boxToValue:
            case boxToStack: {
                printOptional(name.children.get(index), "", "", false);
                return index + 1;
            }
            case constantPropFunction:
            case constantPropGlobal: {
                target += "[";
                printOptional(name.children.get(index), "", "", false);
                target += " : ";
                String t = name.children.get(index + 1).text();
                if (t.isEmpty()) return index + 1;
                String demangedName = "";
                try {
                    demangedName = MMSwiftDemangle.parsedMangledSwiftSymbol(t).description();
                } catch (Exception extension) {
                    demangedName = "";
                }
                if (demangedName.isEmpty()) {
                    target += t;
                } else {
                    target += demangedName;
                }
                target += "]";
                return index + 2;
            }
            case constantPropInteger:
            case constantPropFloat: {
                target += "[";
                printOptional(name.children.get(index), "", "", false);
                target += " : ";
                printOptional(name.children.get(index + 1), "", "", false);
                target += "]";
                return index + 2;
            }
            case constantPropString: {
                target += "[";
                printOptional(name.children.get(index), "", "", false);
                target += " : ";
                printOptional(name.children.get(index + 1), "", "", false);
                target += "'";
                printOptional(name.children.get(index + 2), "", "", false);
                target += "'";
                target += "]";
                return index + 3;
            }
            case closureProp: {
                target += "[";
                printOptional(name.children.get(index), "", "", false);
                target += " : ";
                printOptional(name.children.get(index + 1), "", "", false);
                target += ", Argument Types : [";
                int idx = index + 2;

                while (idx < name.children.size()) {
                    SwiftSymbol c = name.children.get(idx);
                    if (c != null && c.kind == SwiftSymbol.Kind.type) {
                        printName(c, false);
                        idx++;
                        if (idx < name.children.size() && !name.children.get(idx).text().isEmpty()) {
                            target += ", ";
                        }
                    }
                }
                target += "]";
                return idx;
            }
            default:
                printOptional(name.children.get(index), "", "", false);
                return index = 1;
        }
    }

    public void printSpecializationPrefix(SwiftSymbol name, String description, String paramPrefix) {
        if ((options & DISPLAYGENERICSPECIALIZATIONS) != DISPLAYGENERICSPECIALIZATIONS) {
            if (!specializationPrefixPrinted) {
                target += "specialized ";
                specializationPrefixPrinted = true;
            }
            return;
        }
        target += description + " <";
        String separator = "";
        for (SwiftSymbol c : name.children) {
            switch (c.kind) {
                case specializationPassID:
                    break;
                case specializationIsFragile: {
                    target += separator;
                    separator = ", ";
                    printName(c, false);
                }
                break;
                default:
                    if (!c.children.isEmpty()) {
                        target += separator;
                        target += paramPrefix;
                        separator = ", ";
                        printName(c, false);
                    }
            }
        }
        target += "> of ";
    }

    public void printFunctionParameters(SwiftSymbol labelList, SwiftSymbol parameterType, boolean showTypes) {
        if (parameterType.kind != SwiftSymbol.Kind.argumentTuple) return;
        SwiftSymbol t = parameterType.children.get(0);
        if (t == null || t.kind != SwiftSymbol.Kind.type) return;
        SwiftSymbol parameters = t.children.get(0);
        if (parameters == null) return;

        if (parameters.kind != SwiftSymbol.Kind.tuple) {
            if (showTypes) {
                target += "(";
                printName(parameters, false);
                target += ")";
            } else {
                target += "(_:)";
            }
            return;
        }

        target += "(";
        for (int i = 0; i < parameters.children.size(); i++) {
            SwiftSymbol tuple = parameters.children.get(i);
            SwiftSymbol label = null;
            if (labelList != null) {
                label = labelList.children.get(i);
            }

            if (label != null) {
                String text = label.kind == SwiftSymbol.Kind.identifier ? (!label.text().isEmpty() ? label.text() : "") : "_";
                target += text + ":";
                if (showTypes) {
                    target += " ";
                }
            } else if (!showTypes) {
                SwiftSymbol label_ = Utility.First(label.children, swiftSymbol -> swiftSymbol.kind == SwiftSymbol.Kind.tupleElementName);
                if (label_ != null) {
                    target += Utility.validateString(label.text()) + ":";
                } else {
                    target += "_:";
                }
            }
            if (showTypes) {
                printName(tuple, false);
                if (i != parameters.children.size() - 1) {
                    target += ", ";
                }
            }
        }
        target += ")";
    }

    public void printFunctionType(SwiftSymbol labelList, SwiftSymbol name) {
        if (name == null) {
            name = new SwiftSymbol();
        }
        SwiftSymbol first = name.children.get(0);
        int startIndex = 0;
        if (first != null && first.kind == SwiftSymbol.Kind.throwsAnnotation) {
            startIndex = 1;
        }
        SwiftSymbol parameterType = name.children.get(startIndex);
        if (parameterType == null) {
            return;
        }

        boolean show = (options & SHOWFUNCTIONARGUMENTTYPES) == SHOWFUNCTIONARGUMENTTYPES;
        printFunctionParameters(labelList, parameterType, show);
        if (!show) {
            return;
        }
        if (startIndex == 1) {
            target += " throws";
        }
        printOptional(name.children.get(startIndex + 1), "", "", false);
    }

    public void printBoundGenericNoSugar(SwiftSymbol name) {
        SwiftSymbol typeList = name.children.get(1);
        if (typeList == null) {
            return;
        }
        printFirstChild(name, "", "", false);
        printChildren(typeList, "<", ">", ", ");
    }

    public SugarType findSugar(SwiftSymbol name) {
        SwiftSymbol firstChild = name.children.get(0);
        if (firstChild == null) return SugarType.none;
        if (name.children.size() == 1 && firstChild.kind == SwiftSymbol.Kind.type) {
            return findSugar(firstChild);
        }
        if (name.kind != SwiftSymbol.Kind.boundGenericEnum && name.kind != SwiftSymbol.Kind.boundGenericStructure) {
            return SugarType.none;
        }

        SwiftSymbol secondChild = name.children.get(1);
        if (secondChild == null) return SugarType.none;
        if (name.children.size() != 2) return SugarType.none;
        SwiftSymbol unboundType = firstChild.children.get(0);
        if (unboundType == null || unboundType.children.size() <= 1) return SugarType.none;
        SwiftSymbol typeArgs = secondChild;
        SwiftSymbol c0 = unboundType.children.get(0);
        SwiftSymbol c1 = unboundType.children.get(1);
        if (name.kind == SwiftSymbol.Kind.boundGenericEnum) {
            if (typeArgs.children.size() == 1 && c0.isSwiftModule()) {
                if (c1.isIdentifier("Optional")) {
                    return SugarType.optional;
                }
                if (c1.isIdentifier("ImplicitlyUnwrappedOptional")) {
                    return SugarType.implicitlyUnwrappedOptional;
                }
            }
            return SugarType.none;
        }

        if (typeArgs.children.size() == 1 && c0.isSwiftModule()) {
            if (c1.isIdentifier("Array")) {
                return SugarType.optional;
            }
            if (c1.isIdentifier("Dictionary")) {
                return SugarType.implicitlyUnwrappedOptional;
            }
        }
        return SugarType.none;
    }

    public void printBoundGeneric(SwiftSymbol name) {
        if (name.children.size() < 2) return;
        if (name.children.size() == 2 && (options & SYNTHESIZESUGARONTYPES) == SYNTHESIZESUGARONTYPES && name.kind != SwiftSymbol.Kind.boundGenericClass) {
            //guard
        } else {
            printBoundGenericNoSugar(name);
            return;
        }
        if (name.kind == SwiftSymbol.Kind.boundGenericProtocol) {
            printOptional(name.children.get(1), "", "", false);
            printOptional(name.children.get(0), " as ", "", false);
            return;
        }

        SugarType sugarType = findSugar(name);
        switch (sugarType) {
            case optional:
            case implicitlyUnwrappedOptional: {
                SwiftSymbol type = name.children.get(1).children.get(0);
                if (type != null) {
                    boolean needParens = !type.kind.isSimpleType();
                    printOptional(type, needParens ? "(" : "", needParens ? ")" : "", false);
                    target += sugarType == SugarType.optional ? "?" : "!";
                }
            }
            break;
            case array:
            case dictionary: {
                printOptional(name.children.get(1).children.get(0), "[", "", false);
                if (sugarType == SugarType.dictionary) {
                    printOptional(name.children.get(1).children.get(1), " : ", "", false);
                }
                target += "]";
            }
            break;
            default:
                printBoundGenericNoSugar(name);
        }
    }

    public void printImplFunctionType(SwiftSymbol name) {
        int attrs = 1;
        int inputs = 2;
        int results = 3;
        int curState = attrs;

        for (SwiftSymbol c : name.children) {
            if (c.kind == SwiftSymbol.Kind.implParameter) {
                if (curState == inputs) {
                    target += ", ";
                } else if (curState == attrs) {
                    target += "(";
                } else if (curState == results) {
                    break;
                }
                curState = inputs;
                printName(c, false);
            } else if (c.kind == SwiftSymbol.Kind.implResult || c.kind == SwiftSymbol.Kind.implErrorResult) {
                if (curState == inputs) {
                    target += ") -> (";
                } else if (curState == attrs) {
                    target += "() -> (";
                } else if (curState == results) {
                    target += ", ";
                }
                curState = results;
                printName(c, false);
            } else {
                printName(c, false);
                target += " ";
            }
        }

        if (curState == inputs) {
            target += ") -> ()";
        } else if (curState == attrs) {
            target += "() -> ()";
        } else if (curState == results) {
            target += ")";
        }
    }

    public void quotedString(String value) {
        if (value == null) value = "";
        target += "\"";
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\':
                    target += "\\\\";
                    break;
                case '\t':
                    target += "\\t";
                    break;
                case '\n':
                    target += "\\n";
                    break;
                case '\r':
                    target += "\\r";
                    break;
                case '\"':
                    target += "\\\"";
                    break;
                case '\0':
                    target += "\\0";
                    break;
                default: {
                    if (c < 32 || c == 127) {
                        target += "\\x";
                        int charInt = c;
                        char char_ = (char) (((charInt >> 4) > 9) ? ((char) c + 'A') : ((char) c + '0'));
                        target += char_;
                    } else {
                        target += String.valueOf(c);
                    }
                }
            }
        }
        target += "\"";
    }

    public String archetypeName(int index, int depth) {
        String result = "";
        int i = index;
        do {
            result += "A" + (char) (i % 26);
            i /= 26;
        } while (i > 0);

        if (depth != 0) {
            result += depth;
        }
        return result;
    }

}
