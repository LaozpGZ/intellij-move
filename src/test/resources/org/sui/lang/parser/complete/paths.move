module 0x1::paths {
    struct S has copy, drop, store {
        field: u8,
    }

    struct T has copy, drop, store {
        s: S,
    }

    struct Gen<T> has copy, drop, store {
        value: T,
    }

    fun test_simple_path() {
        let s = S { field: 42 };
        assert!(s.field == 42, 0);
    }

    fun test_module_path() {
        use 0x1::paths::S;
        let s = S { field: 42 };
        assert!(s.field == 42, 0);
    }

    fun test_chain_path() {
        let t = T { s: S { field: 42 } };
        assert!(t.s.field == 42, 0);
    }

    fun test_generic_path() {
        let g = Gen<u8> { value: 42 };
        assert!(g.value == 42, 0);
    }
}
