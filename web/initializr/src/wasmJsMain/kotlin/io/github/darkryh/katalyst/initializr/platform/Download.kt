package io.github.darkryh.katalyst.initializr.platform

import io.github.darkryh.katalyst.initializr.zip.Base64
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement

/**
 * The single point of browser interop: turn the in-memory ZIP [bytes] into a `data:` URL and trigger
 * a download via a throwaway anchor element. Everything upstream (generation, zipping, Base64) is pure
 * common code; only this shim touches the DOM.
 */
fun downloadZip(filename: String, bytes: ByteArray) {
    val dataUrl = "data:application/zip;base64," + Base64.encode(bytes)
    val anchor = document.createElement("a") as HTMLAnchorElement
    anchor.href = dataUrl
    anchor.setAttribute("download", filename)
    document.body?.appendChild(anchor)
    anchor.click()
    anchor.remove()
}

/** Read the OS dark-mode preference so the first paint matches the viewer's theme. */
fun prefersDark(): Boolean =
    kotlinx.browser.window.matchMedia("(prefers-color-scheme: dark)").matches
