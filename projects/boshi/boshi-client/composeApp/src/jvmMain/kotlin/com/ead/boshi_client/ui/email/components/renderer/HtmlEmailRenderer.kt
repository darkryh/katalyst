package com.ead.boshi_client.ui.email.components.renderer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import java.awt.Color as AwtColor
import javax.swing.JEditorPane
import javax.swing.JScrollPane

@Composable
fun HtmlEmailRenderer(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    // Sanitize HTML to prevent XSS
    val sanitizedHtml = remember(htmlContent) {
        Jsoup.clean(
            htmlContent,
            Safelist.relaxed()
                .addTags("div", "span", "style")
                .addAttributes("div", "style")
                .addAttributes("span", "style")
                .addAttributes("p", "style")
                .addAttributes("h1", "style")
                .addAttributes("h2", "style")
                .addAttributes("h3", "style")
        )
    }

    SwingPanel(
        modifier = modifier,
        factory = {
            val editorPanel = JEditorPane().apply {
                contentType = "text/html"
                isEditable = false
                background = AwtColor.WHITE
                
                // Set HTML content
                text = """
                    <html>
                    <head>
                        <style>
                            body {
                                font-family: Arial, sans-serif;
                                font-size: 14px;
                                line-height: 1.6;
                                color: #333;
                                padding: 0;
                                margin: 0;
                            }
                            h1, h2, h3 { margin-top: 10px; margin-bottom: 10px; }
                            p { margin: 8px 0; }
                            ul, ol { margin: 10px 0; padding-left: 20px; }
                        </style>
                    </head>
                    <body>
                        $sanitizedHtml
                    </body>
                    </html>
                """.trimIndent()

                // Disable link following
                // isEnabled = false // Removed to prevent GrayFilter NPE on images
            }
            
            JScrollPane(editorPanel).apply {
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                border = null
            }
        }
    )
}

