module 0x1::tuple_struct_fields {
    struct S(u8, bool);

    fun main(s: &S) {
        let S(a, b) = S(1, true);
        let _ = S { 0: 2, 1: false };
        s.0;
        s.1;
        a;
        b;
    }
}

