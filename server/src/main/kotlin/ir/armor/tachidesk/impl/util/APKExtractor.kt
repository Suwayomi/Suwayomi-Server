package ir.armor.tachidesk.impl.util

/*
* Copyright (C) Contributors to the Suwayomi project
*
* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import mu.KotlinLogging
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object APKExtractor {
    private val logger = KotlinLogging.logger {}

    // decompressXML -- Parse the 'compressed' binary form of Android XML docs
    // such as for AndroidManifest.xml in .apk files
    private const val endDocTag = 0x00100101
    private const val startTag = 0x00100102
    private const val endTag = 0x00100103

    private fun decompressXML(xml: ByteArray): String {
        val finalXML = StringBuilder()

        // Compressed XML file/bytes starts with 24x bytes of data,
        // 9 32 bit words in little endian order (LSB first):
        // 0th word is 03 00 08 00
        // 3rd word SEEMS TO BE: Offset at then of StringTable
        // 4th word is: Number of strings in string table
        // WARNING: Sometime I indiscriminently display or refer to word in
        // little endian storage format, or in integer format (ie MSB first).
        val numbStrings = LEW(xml, 4 * 4)

        // StringIndexTable starts at offset 24x, an array of 32 bit LE offsets
        // of the length/string data in the StringTable.
        val sitOff = 0x24 // Offset of start of StringIndexTable

        // StringTable, each string is represented with a 16 bit little endian
        // character count, followed by that number of 16 bit (LE) (Unicode)
        // chars.
        val stOff = sitOff + numbStrings * 4 // StringTable follows
        // StrIndexTable

        // XMLTags, The XML tag tree starts after some unknown content after the
        // StringTable. There is some unknown data after the StringTable, scan
        // forward from this point to the flag for the start of an XML start
        // tag.
        var xmlTagOff = LEW(xml, 3 * 4) // Start from the offset in the 3rd
        // word.
        // Scan forward until we find the bytes: 0x02011000(x00100102 in normal
        // int)
        var ii = xmlTagOff
        while (ii < xml.size - 4) {
            if (LEW(xml, ii) == startTag) {
                xmlTagOff = ii
                break
            }
            ii += 4
        }

        // XML tags and attributes:
        // Every XML start and end tag consists of 6 32 bit words:
        // 0th word: 02011000 for startTag and 03011000 for endTag
        // 1st word: a flag?, like 38000000
        // 2nd word: Line of where this tag appeared in the original source file
        // 3rd word: FFFFFFFF ??
        // 4th word: StringIndex of NameSpace name, or FFFFFFFF for default NS
        // 5th word: StringIndex of Element Name
        // (Note: 01011000 in 0th word means end of XML document, endDocTag)

        // Start tags (not end tags) contain 3 more words:
        // 6th word: 14001400 meaning??
        // 7th word: Number of Attributes that follow this tag(follow word 8th)
        // 8th word: 00000000 meaning??

        // Attributes consist of 5 words:
        // 0th word: StringIndex of Attribute Name's Namespace, or FFFFFFFF
        // 1st word: StringIndex of Attribute Name
        // 2nd word: StringIndex of Attribute Value, or FFFFFFF if ResourceId
        // used
        // 3rd word: Flags?
        // 4th word: str ind of attr value again, or ResourceId of value

        // TMP, dump string table to tr for debugging
        // tr.addSelect("strings", null);
        // for (int ii=0; ii<numbStrings; ii++) {
        // // Length of string starts at StringTable plus offset in StrIndTable
        // String str = compXmlString(xml, sitOff, stOff, ii);
        // tr.add(String.valueOf(ii), str);
        // }
        // tr.parent();

        // Step through the XML tree element tags and attributes
        var off = xmlTagOff
        var indent = 0
        var startTagLineNo = -2
        while (off < xml.size) {
            val tag0 = LEW(xml, off)
            // int tag1 = LEW(xml, off+1*4);
            val lineNo = LEW(xml, off + 2 * 4)
            // int tag3 = LEW(xml, off+3*4);
            val nameNsSi = LEW(xml, off + 4 * 4)
            val nameSi = LEW(xml, off + 5 * 4)
            if (tag0 == startTag) { // XML START TAG
                val tag6 = LEW(xml, off + 6 * 4) // Expected to be 14001400
                val numbAttrs = LEW(xml, off + 7 * 4) // Number of Attributes
                // to follow
                // int tag8 = LEW(xml, off+8*4); // Expected to be 00000000
                off += 9 * 4 // Skip over 6+3 words of startTag data
                val name = compXmlString(xml, sitOff, stOff, nameSi)
                // tr.addSelect(name, null);
                startTagLineNo = lineNo

                // Look for the Attributes
                val sb = StringBuffer()
                for (ii in 0 until numbAttrs) {
                    val attrNameNsSi = LEW(xml, off) // AttrName Namespace Str
                    // Ind, or FFFFFFFF
                    val attrNameSi = LEW(xml, off + 1 * 4) // AttrName String
                    // Index
                    val attrValueSi = LEW(xml, off + 2 * 4) // AttrValue Str
                    // Ind, or
                    // FFFFFFFF
                    val attrFlags = LEW(xml, off + 3 * 4)
                    val attrResId = LEW(xml, off + 4 * 4) // AttrValue
                    // ResourceId or dup
                    // AttrValue StrInd
                    off += 5 * 4 // Skip over the 5 words of an attribute
                    val attrName = compXmlString(
                            xml, sitOff, stOff,
                            attrNameSi
                    )
                    val attrValue = if (attrValueSi != -1) compXmlString(xml, sitOff, stOff, attrValueSi)
                    else "resourceID 0x ${Integer.toHexString(attrResId)}"
                    sb.append(" $attrName=\"$attrValue\"")
                    // tr.add(attrName, attrValue);
                }
                finalXML.append("<$name$sb>")
                prtIndent(indent, "<$name$sb>")
                indent++
            } else if (tag0 == endTag) { // XML END TAG
                indent--
                off += 6 * 4 // Skip over 6 words of endTag data
                val name = compXmlString(xml, sitOff, stOff, nameSi)
                finalXML.append("</$name>")
                prtIndent(
                        indent,
                        "</" + name + "> (line " + startTagLineNo +
                                "-" + lineNo + ")"
                )
                // tr.parent(); // Step back up the NobTree
            } else if (tag0 == endDocTag) { // END OF XML DOC TAG
                break
            } else {
                logger.debug(
                        "  Unrecognized  tag code '${Integer.toHexString(tag0)}'' at offset $off"
                )
                break
            }
        } // end of while loop scanning tags and attributes of XML tree
        logger.debug("    end at offset $off");
        return finalXML.toString()
    } // end of decompressXML

    private fun compXmlString(xml: ByteArray, sitOff: Int, stOff: Int, strInd: Int): String? {
        if (strInd < 0) return null
        val strOff = stOff + LEW(xml, sitOff + strInd * 4)
        return compXmlStringAt(xml, strOff)
    }

    var spaces = "                                             "
    fun prtIndent(indent: Int, str: String) {
        logger.debug(spaces.substring(0, Math.min(indent * 2, spaces.length)) + str)
    }

    // compXmlStringAt -- Return the string stored in StringTable format at
    // offset strOff. This offset points to the 16 bit string length, which
    // is followed by that number of 16 bit (Unicode) chars.
    private fun compXmlStringAt(arr: ByteArray, strOff: Int): String {
        val strLen: Int = arr[strOff + 1].toInt() shl 8 and 0xff00 or arr[strOff].toInt() and 0xff
        val chars = ByteArray(strLen)
        for (ii in 0 until strLen) {
            chars[ii] = arr[strOff + 2 + ii * 2]
        }
        return String(chars) // Hack, just use 8 byte chars
    } // end of compXmlStringAt

    // LEW -- Return value of a Little Endian 32 bit word from the byte array
    // at offset off.
    private fun LEW(arr: ByteArray, off: Int): Int {

        return (arr[off + 3].toInt() shl 24) and -0x1000000 or
                (arr[off + 2].toInt() shl 16 and 0xff0000) or
                (arr[off + 1].toInt() shl 8 and 0xff00) or
                (arr[off].toInt() and 0xFF)
    } // end of LEW

    @Throws(Exception::class)
    private fun loadXMLFromString(xml: String?): Document {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml)))
    }

    @Throws(IOException::class)
    fun extractDexAndReadClassname(filePath: String, dexPath: String): String {
        ZipFile(filePath).use { zip ->
            val androidManifest = zip.getEntry("AndroidManifest.xml")
            val classesDex = zip.getEntry("classes.dex")

            // write dex file
            zip.getInputStream(classesDex).use { dexInputStream ->
                Files.newOutputStream(Paths.get(dexPath)).use { fileOutputStream ->
                    dexInputStream.copyTo(fileOutputStream)
                }
            }

            // read xml file
            val xml = zip.getInputStream(androidManifest).use { inpStream ->
                // 1024000 = 100 kb
                ByteArray(1024000).let {
                    inpStream.read(it)
                    decompressXML(it)
                }
            }
            try {
                val xmlDoc = loadXMLFromString(xml)
                val pkg = xmlDoc.documentElement.getAttribute("package")
                val nodes = xmlDoc.getElementsByTagName("meta-data")
                for (i in 0 until nodes.length) {
                    val attributes = nodes.item(i).attributes
                    logger.debug(attributes.getNamedItem("name").nodeValue)
                    if (attributes.getNamedItem("name").nodeValue == "tachiyomi.extension.class") {
                        return pkg + attributes.getNamedItem("value").nodeValue
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return ""
        }
    }
}