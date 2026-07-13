import org.apache.commons.compress.archivers.zip.ZipFile
import pxb.android.arsc.ArscDumper
import pxb.android.arsc.ArscParser
import pxb.android.axml.AxmlParser
import suwayomi.tachidesk.manga.impl.util.AndroidManifestParser
import suwayomi.tachidesk.manga.impl.util.ResourceArscIconParser
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.test.Test

class JarTest {

    @Test
    fun jarTest() {
        println(Path("./").absolutePathString())
        val jar = Path("./build/tachiyomi-all.ahottie-v1.4.3.jar")

        val zip = ZipFile.builder()
            .setPath(jar)
            .get()
        val manifest = zip.getInputStream(zip.getEntry("AndroidManifest.xml"))
            .use {
                AndroidManifestParser.parse(it)
            }
        //
        // reader.parse()
        // println(reader.resourceTable)
        // val resourceTable = reader.resourceTable
        // println(resourceTable.stringPool)
        // //println(table.stringPool.get(1))
        // // table::class.members.forEach {
        // //     if (it.name == "packageMap") {
        // //         it.isAccessible = true
        // //         val map = it.call(table) as Map<Short, ResourcePackage>
        // //         map.forEach { short, packagz ->
        // //             println(short)
        // //             println(packagz.name to packagz.id)
        // //         }
        // //     }
        // // }
        // val apkTranslator = ApkMetaTranslator(resourceTable, null)
        // val xmlTranslator = XmlTranslator()
        // val xmlStreamer = CompositeXmlStreamer(xmlTranslator, apkTranslator)
        // val data = zip.getInputStream(zip.getEntry("AndroidManifest.xml"))
        //     .use {
        //         it.readBytes()
        //     }
        //
        //
        // val buffer = ByteBuffer.wrap(data)
        // val binaryXmlParser = BinaryXmlParser(buffer, resourceTable)
        // //binaryXmlParser.locale = preferredLocale
        // binaryXmlParser.xmlStreamer = xmlStreamer
        // binaryXmlParser.parse()
        // val manifestXml = xmlTranslator.xml
        // println(manifestXml)
        // val iconPaths = apkTranslator.iconPaths
        // iconPaths.forEach {
        //     println(it.path)
        // }

        // val parser = zip.getInputStream(zip.getEntry("resources.arsc"))
        //     .use {
        //         ArscParser(it.readBytes())
        //     }
        // val parsed = parser.parse()
        // ArscDumper.dump(parsed)
        //
        // parsed.forEach {
        //     print("pkg.name: ")
        //     println(it.name)
        //     it.types.toList().forEachIndexed { t, (i, type) ->
        //         print("pkg.types.$t.type.name: ")
        //         println(type.name)
        //         print("pkg.types.$t.type.id: ")
        //         println(type.id)
        //         type.specs.forEachIndexed { y, spec ->
        //             print("pkg.types.$t.type.specs.$y.id: ")
        //             println(spec.id)
        //             print("pkg.types.$t.type.specs.$y.name: ")
        //             println(spec.name)
        //             print("pkg.types.$t.type.specs.$y.flags: ")
        //             println(spec.flags)
        //         }
        //         type.configs.forEachIndexed { y, config ->
        //             print("pkg.types.$t.type.configs.$y.id: ")
        //             println(config.id.toHexString(HexFormat.UpperCase))
        //             print("pkg.types.$t.type.configs.$y.entryCount: ")
        //             println(config.entryCount)
        //             config.resources.forEach {
        //                 print("pkg.types.$t.type.configs.$y.resources.${it.key}.flag: ")
        //                 println(it.value.flag)
        //                 print("pkg.types.$t.type.configs.$y.resources.${it.key}.value: ")
        //                 println(it.value.value)
        //                 print("pkg.types.$t.type.configs.$y.resources.${it.key}.spec.id: ")
        //                 println(it.value.spec.id)
        //                 print("pkg.types.$t.type.configs.$y.resources.${it.key}.spec.name: ")
        //                 println(it.value.spec.name)
        //                 print("pkg.types.$t.type.configs.$y.resources.${it.key}.spec.flags: ")
        //                 println(it.value.spec.flags)
        //             }
        //         }
        //     }
        // }

        // val fileTest = Path("./build/icon.png")
        // ResourceArscIconParser.extractIcon(jar, fileTest)

        println(manifest.packageName)
    }
}
