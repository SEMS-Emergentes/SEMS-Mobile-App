package pe.edu.upc.sems.shared.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLinksTest {

    private val web = "https://sems-web-application.vercel.app"

    private fun token(link: String?) = AppLinks.resetPasswordToken(link, webAppUrl = web)

    @Test
    fun `reads the token of the reset password link`() {
        assertEquals("abc-123", token("$web/reset-password?token=abc-123"))
        assertEquals("a b", token("$web/reset-password/?utm=x&token=a%20b"))
    }

    @Test
    fun `ignores other links`() {
        assertNull(token(null))
        assertNull(token(""))
        assertNull(token("$web/reset-password"))
        assertNull(token("$web/reset-password?token="))
        assertNull(token("$web/login?token=abc"))
        assertNull(token("https://evil.example.com/reset-password?token=abc"))
        assertNull(token("http://sems-web-application.vercel.app/reset-password?token=abc"))
        assertNull(token("not a link"))
    }
}
