module 0x1::revised_paths {
    use ::0x1::revised_paths::Self;
    use ::std::vector;
    use ::sui::coin;

    fun test_global_paths() {
        let v = ::std::vector::empty<u8>();
        let c: ::sui::coin::Coin<::sui::sui::SUI>;
        let _ = (v, c);
    }
}
