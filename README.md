## SwiftDemangle
A translation (line-by-line in many cases) of Swift's Demangler.cpp into Java.

other version for demangling swift.

[Swift: CwlDemangle.swift](https://github.com/mattgallagher/CwlDemangle)

[C++: Demangler.cpp](https://github.com/apple/swift/blob/master/lib/Demangling/Demangler.cpp)

### Usage
Parse a String containing a mangled Swift symbol with the parseMangledSwiftSymbol function:

```java
String res = MMSwiftDemangle.parseMangledSwiftSymbolToString("$s3Demo21SourcesViewControllerCfETo [<compiler-generated> : 25106 + 0x40]");
```


