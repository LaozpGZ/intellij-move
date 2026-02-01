module 0x1::labels {
    fun main() {
        'outer: loop {
            'inner: loop {
                if (true) {
                    break 'inner;
                }
                continue 'outer;
            }
        }

        'label1: while (true) {
            if (true) {
                break 'label1;
            }
        }

        'label2: for (i in 0..10) {
            if (i == 5) {
                continue 'label2;
            }
        }
    }
}
