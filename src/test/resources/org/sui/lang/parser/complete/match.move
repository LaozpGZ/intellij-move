module 0x1::test_match {
    enum Color {
        Red,
        Green,
        Blue,
    }

    enum Option<T> {
        Some(T),
        None,
    }

    fun test_color_match(c: Color): u8 {
        match c {
            Color::Red => 0,
            Color::Green => 1,
            Color::Blue => 2,
        }
    }

    fun test_option_match(opt: Option<u8>): u8 {
        match opt {
            Option::Some(x) => x,
            Option::None => 0,
        }
    }

    fun test_match_with_guard(x: u8): bool {
        match x {
            0 => true,
            n if n > 5 => true,
            _ => false,
        }
    }
}
