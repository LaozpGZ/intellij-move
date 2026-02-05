package org.sui.toml

import org.sui.utils.tests.annotation.AnnotatorTestCase

class MoveTomlErrorAnnotatorTest : AnnotatorTestCase(MoveTomlErrorAnnotator::class) {
    fun `test valid addresses`() = checkMoveTomlWarnings(
        """
        [addresses]
        addr0 = "_"
        addr1 = "0x1"
        addr2 = "0x42"
        addr3 = "0x4242424242424242424242424242424242424242424242424242420000000000"
        addr4 = "4242424242424242424242424242424242424242424242424242420000000000"
    """
    )

    fun `test invalid symbols in address`() = checkMoveTomlWarnings(
        """
        [addresses]
        addr2 = "0x<error descr="Invalid address: only hex symbols are allowed">helloworld</error>"
    """
    )

    fun `test address is too long`() = checkMoveTomlWarnings(
        """
        [addresses]
        addr3 = "0x<error descr="Invalid address: no more than 64 symbols allowed">424242424242424242424242424242424242424242424242424242000000000011122</error>"
    """
    )

    fun `test valid edition values`() = checkMoveTomlWarnings(
        """
        [package]
        name = "test_pkg"
        edition = "1"
    """
    )

    fun `test valid edition value 2024`() = checkMoveTomlWarnings(
        """
        [package]
        name = "test_pkg"
        edition = "2024"
    """
    )

    fun `test valid edition value 2024 alpha`() = checkMoveTomlWarnings(
        """
        [package]
        name = "test_pkg"
        edition = "2024.alpha"
    """
    )

    fun `test invalid edition value`() = checkMoveTomlWarnings(
        """
        [package]
        name = "test_pkg"
        edition = "<error descr="Invalid edition: expected one of 1, 2024, 2024.alpha">2024.beta</error>"
    """
    )

    fun `test edition requires string literal`() = checkMoveTomlWarnings(
        """
        [package]
        name = "test_pkg"
        edition = <error descr="Invalid edition: expected a string literal">2024</error>
    """
    )
}
