module 0x1::break_values {
    fun main() {
        let _loop_value = loop {
            break 1;
        };

        let _ = 'outer: loop {
            'inner: loop {
                break 'outer 2;
            };
        };

        while (true) {
            break 3;
        };

        for (i in 0..10) {
            break i;
        };
    }
}
