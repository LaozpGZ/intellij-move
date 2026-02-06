package org.sui.ide.refactoring.optimizeImports

import org.sui.ide.inspections.fixes.CompilerV2Feat.RECEIVER_STYLE_FUNCTIONS
import org.sui.utils.tests.CompilerV2Features

class OptimizeImportsTest : OptimizeImportsTestBase() {
    fun `test remove unused struct import`() = doTest(
        """
        module 0x1::M {
            struct MyStruct {}
            public fun call() {}
        }        
        script {
            use 0x1::M::MyStruct;
            use 0x1::M::call;
            
            fun main() {
                let a = call();
            }
        }
    """, """
        module 0x1::M {
            struct MyStruct {}
            public fun call() {}
        }        
        script {
            use 0x1::M::call;
            
            fun main() {
                let a = call();
            }
        }
    """
    )

    fun `test remove unused import from group in the middle`() = doTest(
        """
        module 0x1::M {
            struct MyStruct {}
            public fun call() {}
            public fun aaa() {}
        }        
        script {
            use 0x1::M::{aaa, MyStruct, call};
            
            fun main() {
                let a = call();
                let a = aaa();
            }
        }
    """, """
        module 0x1::M {
            struct MyStruct {}
            public fun call() {}
            public fun aaa() {}
        }        
        script {
            use 0x1::M::{aaa, call};
            
            fun main() {
                let a = call();
                let a = aaa();
            }
        }
    """
    )

    fun `test remove unused import from group in the beginning`() = doTest(
        """
        module 0x1::M {
            struct Bbb {}
            public fun call() {}
            public fun aaa() {}
        }        
        script {
            use 0x1::M::{aaa, Bbb, call};
            
            fun main() {
                let a: Bbb = call();
            }
        }
    """, """
        module 0x1::M {
            struct Bbb {}
            public fun call() {}
            public fun aaa() {}
        }        
        script {
            use 0x1::M::{Bbb, call};
            
            fun main() {
                let a: Bbb = call();
            }
        }
    """
    )

    fun `test remove unused import from group in the end`() = doTest(
        """
        module 0x1::M {
            struct Bbb {}
            public fun call() {}
            public fun aaa() {}
        }        
        script {
            use 0x1::M::{aaa, Bbb, call};
            
            fun main() {
                let a: Bbb = aaa();
            }
        }
    """, """
        module 0x1::M {
            struct Bbb {}
            public fun call() {}
            public fun aaa() {}
        }        
        script {
            use 0x1::M::{aaa, Bbb};
            
            fun main() {
                let a: Bbb = aaa();
            }
        }
    """
    )

    fun `test remove curly braces`() = doTest(
        """
        module 0x1::M {
            struct MyStruct {}
            public fun call() {}
        }        
        script {
            use 0x1::M::{call};
            
            fun main() {
                let a = call();
            }
        }
    """, """
        module 0x1::M {
            struct MyStruct {}
            public fun call() {}
        }        
        script {
            use 0x1::M::call;
            
            fun main() {
                let a = call();
            }
        }
    """
    )

    fun `test remove unused module import`() = doTest(
        """
        module 0x1::M {}
        module 0x1::M2 {
            use 0x1::M;
        }        
    """, """
        module 0x1::M {}
        module 0x1::M2 {}        
    """
    )

    fun `test remove unused import group with two items`() = doTest(
        """
        module 0x1::M {
            struct BTC {}
            struct USDT {}
        }        
        module 0x1::Main {
            use 0x1::M::{BTC, USDT};
        }
    """, """
        module 0x1::M {
            struct BTC {}
            struct USDT {}
        }        
        module 0x1::Main {}
    """
    )

    fun `test sort imports std first non test_only first case insensitive`() = doTest(
        """
        module AAA::M1 {
            struct S1 {} 
            struct SS1 {} 
        }        
        module BBB::M2 {
            struct S2 {}
        }
        module 0x1::Main {
            use BBB::M2::S2;
            use AAA::M1::S1;
            use AAA::M1::SS1;
            use aptos_std::table;
            use aptos_std::iter_table;
            use aptos_framework::coin;
            #[test_only]
            use Std::Errors;
            use Std::Signer;
            use std::signature;
        
            fun call(a: S1, b: S2, c: SS1) {
                Signer::address_of();
                signature::;
                table::;
                iter_table::;
                coin::;
            }
        
            #[test]
            fun test() {
                Errors::;
            }
        }
    """, """
        module AAA::M1 {
            struct S1 {} 
            struct SS1 {} 
        }        
        module BBB::M2 {
            struct S2 {}
        }
        module 0x1::Main {
            use Std::Signer;
            use std::signature;
            use aptos_std::iter_table;
            use aptos_std::table;
            use aptos_framework::coin;
        
            use AAA::M1::S1;
            use AAA::M1::SS1;
            use BBB::M2::S2;
        
            #[test_only]
            use Std::Errors;
        
            fun call(a: S1, b: S2, c: SS1) {
                Signer::address_of();
                signature::;
                table::;
                iter_table::;
                coin::;
            }
        
            #[test]
            fun test() {
                Errors::;
            }
        }
    """
    )

    fun `test remove all imports if not needed`() = doTest(
        """
module Std::Errors {}        
module Std::Signer {}        
module AAA::M1 {
    struct S1 {} 
    struct SS1 {} 
}        
module BBB::M2 {
    struct S2 {}
}
module 0x1::Main {
    use Std::Errors;
    use Std::Signer;

    use AAA::M1::S1;
    use AAA::M1::SS1;
    use BBB::M2::S2;

    #[test]
    fun call() {}
}
    """, """
module Std::Errors {}        
module Std::Signer {}        
module AAA::M1 {
    struct S1 {} 
    struct SS1 {} 
}        
module BBB::M2 {
    struct S2 {}
}
module 0x1::Main {
    #[test]
    fun call() {}
}
    """
    )

    fun `test removes empty group`() = doTest(
        """
module 0x1::M1 {}         
module 0x1::Main {
    use 0x1::M1::{};
}        
    """, """
module 0x1::M1 {}         
module 0x1::Main {}        
    """
    )

    fun `test module spec`() = doTest(
        """
module 0x1::string {}        
spec 0x1::main {
    use 0x1::string;
}        
    """, """
module 0x1::string {}        
spec 0x1::main {}        
    """
    )

    fun `test duplicate struct import`() = doTest(
        """
module 0x1::pool { 
    struct X1 {}    
    public fun create_pool<BinStep>() {}        
}        
module 0x1::main {
    use 0x1::pool::{Self, X1, X1};

    fun main() {
        pool::create_pool<X1>();
    }
}        
    """, """
module 0x1::pool { 
    struct X1 {}    
    public fun create_pool<BinStep>() {}        
}        
module 0x1::main {
    use 0x1::pool::{Self, X1};

    fun main() {
        pool::create_pool<X1>();
    }
}        
    """
    )

    fun `test duplicate self import`() = doTest(
        """
        module 0x1::pool { 
            struct X1 {}    
            public fun create_pool<BinStep>() {}        
        }        
        module 0x1::main {
            use 0x1::pool::{Self, Self, X1};
        
            fun main() {
                pool::create_pool<X1>();
            }
        }        
    """, """
        module 0x1::pool { 
            struct X1 {}    
            public fun create_pool<BinStep>() {}        
        }        
        module 0x1::main {
            use 0x1::pool::{Self, X1};
        
            fun main() {
                pool::create_pool<X1>();
            }
        }        
    """
    )

    @CompilerV2Features(RECEIVER_STYLE_FUNCTIONS)
    fun `test remove unused use fun import`() = doTest(
        """
        module 0x1::M {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
        }
        module 0x1::Main {
            use fun 0x1::M::call as 0x1::M::S.alias_call;

            fun main() {}
        }
    """,
        """
        module 0x1::M {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
        }
        module 0x1::Main {
            fun main() {}
        }
    """
    )

    @CompilerV2Features(RECEIVER_STYLE_FUNCTIONS)
    fun `test remove unused public use fun import`() = doTest(
        """
        module 0x1::M {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
        }
        module 0x1::Main {
            public use fun 0x1::M::call as 0x1::M::S.alias_call;

            fun main() {}
        }
    """,
        """
        module 0x1::M {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
        }
        module 0x1::Main {
            fun main() {}
        }
    """
    )

    @CompilerV2Features(RECEIVER_STYLE_FUNCTIONS)
    fun `test keep used use fun import`() = doTest(
        """
        module 0x1::M {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
        }
        module 0x1::Main {
            use fun 0x1::M::call as 0x1::M::S.alias_call;

            fun main(s: &0x1::M::S) {
                s.alias_call();
            }
        }
    """,
        """
        module 0x1::M {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
        }
        module 0x1::Main {
            use fun 0x1::M::call as 0x1::M::S.alias_call;

            fun main(s: &0x1::M::S) {
                s.alias_call();
            }
        }
    """
    )

    @CompilerV2Features(RECEIVER_STYLE_FUNCTIONS)
    fun `test sort use and use fun imports`() = doTest(
        """
        module 0x1::A {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
            public fun f() {}
        }
        module 0x1::B {
            public struct T has copy, drop {}
            public fun call(self: &T) {}
            public fun g() {}
        }
        module 0x1::Main {
            public use fun 0x1::B::call as 0x1::B::T.b_call;
            use 0x1::B::g;
            use fun 0x1::A::call as 0x1::A::S.a_call;
            use 0x1::A::f;

            fun main(a: &0x1::A::S, b: &0x1::B::T) {
                f();
                g();
                a.a_call();
                b.b_call();
            }
        }
    """,
        """
        module 0x1::A {
            public struct S has copy, drop {}
            public fun call(self: &S) {}
            public fun f() {}
        }
        module 0x1::B {
            public struct T has copy, drop {}
            public fun call(self: &T) {}
            public fun g() {}
        }
        module 0x1::Main {
            use 0x1::A::f;
            use 0x1::B::g;

            use fun 0x1::A::call as 0x1::A::S.a_call;

            public use fun 0x1::B::call as 0x1::B::T.b_call;

            fun main(a: &0x1::A::S, b: &0x1::B::T) {
                f();
                g();
                a.a_call();
                b.b_call();
            }
        }
    """
    )

//    fun `test module spec with parent import`() = doTest("""
//module 0x1::string { public fun utf8(v: vector<u8>) {} }
//module 0x1::main {
//    use 0x1::string;
//
//    fun main() {
//        let _a = string::utf8(b"hello");
//    }
//}
//spec 0x1::main {
//    use 0x1::string;
//
//    spec main {
//        let _a = string::utf8(b"hello");
//    }
//}
//    """, """
//module 0x1::string { public fun utf8(v: vector<u8>) {} }
//module 0x1::main {
//    use 0x1::string;
//
//    fun main() {
//        let _a = string::utf8(b"hello");
//    }
//}
//spec 0x1::main {
//    spec main {
//        let _a = string::utf8(b"hello");
//    }
//}
//    """)
}
