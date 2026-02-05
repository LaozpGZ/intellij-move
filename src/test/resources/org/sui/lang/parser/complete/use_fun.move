module 0x1::M {
    struct S {}
    use fun 0x1::M::call as S.call;
    public use fun 0x1::M::call as S.call;
    public fun call() {}
}
