module 0x1::macros {
    struct S has copy, drop {
        value: u64
    }

    public inline fun map<Element, NewElement>(
        v: vector<Element>,
        f: |Element|NewElement,
    ): vector<NewElement> {
        foreach(v, |elem| push_back(&mut result, f(elem)));
    }

    inline fun filter<Element: drop>(
        p: |&Element|
    ) {
        foreach(v, |elem| {
            if (p(&elem)) push_back(&mut result, elem);
        });
    }

    inline fun fold<Element>(a: (Element),
                             f: (|Element| Element),
                             g: |Element, (|Element| Element)| Element): Element {
        f(a, g)
    }

    public macro fun passthrough<$T>($x: $T): $T {
        $x
    }

    public macro fun wrap(self: &S, $value: u64): u64 {
        $value
    }

    fun test_macros(s: S) {
        let _a = option!(1);
        let _b = result!(1, 2);
        let _c = bcs!(1);
        let _d = passthrough!(1);
        let _e = s.wrap!(1);
        let _f = map_macro!(vector[1, 2, 3], |x| x + 1);
    }

    public macro fun map_macro<$T, $U>($v: vector<$T>, $f: |$T| $U): vector<$U> {
        vector[]
    }
}
