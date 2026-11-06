package example.nucleus.player

import example.nucleus.utils.cipher.CipherDeobfuscator
import example.nucleus.utils.cipher.PlayerJsFetcher
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptDecoder

/**
 * Bridges PipePipeExtractor's JavaScript-decoder contract to PaltaSound's
 * existing local player.js solver.
 *
 * This keeps extraction local: PipePipeExtractor otherwise calls
 * `api.pipepipe.dev/decoder`, which is both a privacy leak and an external
 * dependency we do not want for a desktop player.
 */
class PipePipeLocalDecoder : YoutubeJavaScriptDecoder {
    override fun getPlayerData(videoId: String): YoutubeJavaScriptDecoder.PlayerData {
        val player = runBlocking { PlayerJsFetcher.getPlayerJs() }
            ?: throw ParsingException("No se pudo descargar el player.js de YouTube")
        val signatureTimestamp = runBlocking { PlayerJsFetcher.getSignatureTimestamp() }
            ?: throw ParsingException("No se pudo extraer signatureTimestamp del player.js")
        return YoutubeJavaScriptDecoder.PlayerData(player.second, signatureTimestamp)
    }

    override fun decodeBatch(
        playerId: String,
        signatures: MutableList<String>?,
        throttlingParameters: MutableList<String>?,
    ): YoutubeApiDecoder.BatchDecodeResult {
        val decodedSignatures = signatures.orEmpty().associateWith { value ->
            runBlocking { CipherDeobfuscator.deobfuscateSignature(value) }
                ?: throw ParsingException("No se pudo decodificar la firma del player $playerId")
        }
        val decodedN = throttlingParameters.orEmpty().associateWith { value ->
            runBlocking { CipherDeobfuscator.deobfuscateNParam(value) }
                ?: throw ParsingException("No se pudo decodificar el parámetro n del player $playerId")
        }
        return YoutubeApiDecoder.BatchDecodeResult(decodedSignatures, decodedN)
    }
}
