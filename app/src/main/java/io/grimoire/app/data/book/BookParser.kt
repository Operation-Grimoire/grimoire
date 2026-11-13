package io.grimoire.app.data.book

import java.io.File

interface BookParser {
    fun parse(file: File, originalName: String?): ParsedBook
}
