module 0x1::enums {
    enum Color {
        Red,
        Green,
        Blue,
    }

    enum Option<T> {
        Some(T),
        None,
    }

    fun test_color() {
        let c = Color::Red;
        assert!(c == Color::Red, 0);
    }

    fun test_option() {
        let opt = Option::Some(42);
        assert!(opt != Option::None, 0);
    }
}
