module 0x1::test {
    fun call<T>(a: T, b: T): T {
        b
    }

    fun main() {
        call<u8>(1u64);
    }
}