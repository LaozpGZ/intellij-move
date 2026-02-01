module 0x1::public_visibility {

    public(script) fun script_function(): u8 {
        0
    }

    public(friend) fun friend_function(): u8 {
        1
    }

    public(package) fun package_function(): u8 {
        2
    }

    public fun public_function(): u8 {
        3
    }
}
