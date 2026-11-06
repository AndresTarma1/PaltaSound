package example.nucleus.player

import example.nucleus.platform.Platform
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.foreign.ValueLayout.JAVA_SHORT
import java.lang.invoke.MethodHandle
import java.util.logging.Logger

/**
 * Shared D3D11 render target consumed by Nucleus' `TextureView`.
 *
 * libmpv's D3D11 render API draws into [texture] on [device]; [sharedHandle]
 * is the legacy DXGI handle Nucleus imports into the Compose scene. The
 * texture carries a keyed mutex, so the producer brackets every render and the
 * compositor pulls without tearing.
 *
 * The texture lives for the whole app lifetime and has a fixed size: layout
 * changes (windowed, fullscreen, mini player) only scale it in Compose. That
 * is the whole point of this path: no HWND, and no per-layout SetWindowPos.
 */
internal class MpvD3D11Output private constructor(
    private val arena: Arena,
    val width: Int,
    val height: Int,
    val device: MemorySegment,
    val context: MemorySegment,
    val texture: MemorySegment,
    private val keyedMutex: MemorySegment?,
    val sharedHandle: Long,
    private val releaseDevice: MethodHandle,
    private val releaseContext: MethodHandle,
    private val releaseTexture: MethodHandle,
    private val releaseMutex: MethodHandle?,
    private val acquireSync: MethodHandle?,
    private val releaseSync: MethodHandle?,
) : AutoCloseable {
    @Volatile
    private var closed = false

    fun acquire(timeoutMs: Int = 2_000): Boolean {
        val fn = acquireSync ?: return true
        if (closed) return false
        return runCatching {
            (fn.invokeWithArguments(keyedMutex, 0L, timeoutMs) as Int) >= 0
        }.getOrDefault(false)
    }

    fun release() {
        val fn = releaseSync ?: return
        val mutex = keyedMutex ?: return
        if (closed) return
        runCatching { fn.invokeWithArguments(mutex, 0L) }
    }

    /** True when the texture carries a keyed mutex (tear-free import path). */
    val hasKeyedMutex: Boolean get() = keyedMutex != null

    /** Plain shared textures need an explicit flush after each render. */
    fun flush() {
        if (closed) return
        runCatching {
            val fn = comMethod(context, 110, FunctionDescriptor.ofVoid(ADDRESS))
            fn.invokeWithArguments(context)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { releaseMutex?.invokeWithArguments(keyedMutex) }
        runCatching { releaseTexture.invokeWithArguments(texture) }
        runCatching { releaseContext.invokeWithArguments(context) }
        runCatching { releaseDevice.invokeWithArguments(device) }
        runCatching { arena.close() }
    }

    companion object {
        private val log = Logger.getLogger("MpvD3D11Output")
        private val linker: Linker = Linker.nativeLinker()

        private const val D3D_DRIVER_TYPE_HARDWARE = 1
        private const val D3D_DRIVER_TYPE_WARP = 5
        private const val D3D11_SDK_VERSION = 7
        private const val D3D11_CREATE_DEVICE_BGRA_SUPPORT = 0x20
        private const val D3D11_CREATE_DEVICE_VIDEO_SUPPORT = 0x800
        private const val DXGI_FORMAT_R8G8B8A8_UNORM = 28
        private const val D3D11_USAGE_DEFAULT = 0
        private const val D3D11_BIND_SHADER_RESOURCE = 0x8
        private const val D3D11_BIND_RENDER_TARGET = 0x20
        private const val D3D11_RESOURCE_MISC_SHARED_KEYEDMUTEX = 0x100

        // ID3D11Multithread (d3d11_4.h)
        private val IID_ID3D11_MULTITHREAD = guid(0x9b7e4e00.toInt(), 0x342c, 0x4106, 0xa1, 0x9f, 0x4f, 0x27, 0x04, 0xf6, 0x89, 0xf0)
        // IDXGIResource (dxgi.h)
        private val IID_IDXGI_RESOURCE = guid(0x035f3ab4, 0x482e, 0x4e50, 0xb4, 0x1f, 0x8a, 0x7f, 0x8b, 0xd8, 0x96, 0x0b)
        // IDXGIKeyedMutex (dxgi.h)
        private val IID_IDXGI_KEYED_MUTEX = guid(0x9d8e1289.toInt(), 0xd7b3, 0x465f, 0x81, 0x26, 0x25, 0x0e, 0x34, 0x9a, 0xf8, 0x5d)

        fun create(width: Int = 1920, height: Int = 1080): MpvD3D11Output? {
            if (!Platform.isWindows) return null
            val w = width.coerceAtLeast(2)
            val h = height.coerceAtLeast(2)
            return try {
                open(w, h)
            } catch (t: Throwable) {
                log.warning("No se pudo crear la textura D3D11: ${t.message}")
                null
            }
        }

        private fun open(width: Int, height: Int): MpvD3D11Output? {
            val arena = Arena.ofShared()
            try {
                val d3d11 = SymbolLookup.libraryLookup("d3d11", arena)
                val createDevice = linker.downcallHandle(
                    d3d11.find("D3D11CreateDevice").orElseThrow(),
                    FunctionDescriptor.of(
                        JAVA_INT,
                        ADDRESS,
                        JAVA_INT,
                        ADDRESS,
                        JAVA_INT,
                        ADDRESS,
                        JAVA_INT,
                        JAVA_INT,
                        ADDRESS,
                        ADDRESS,
                        ADDRESS,
                    ),
                )

                val created = tryCreateDevice(
                    arena,
                    createDevice,
                    D3D_DRIVER_TYPE_HARDWARE,
                    D3D11_CREATE_DEVICE_BGRA_SUPPORT or D3D11_CREATE_DEVICE_VIDEO_SUPPORT,
                ) ?: tryCreateDevice(
                    arena,
                    createDevice,
                    D3D_DRIVER_TYPE_WARP,
                    D3D11_CREATE_DEVICE_BGRA_SUPPORT or D3D11_CREATE_DEVICE_VIDEO_SUPPORT,
                ) ?: tryCreateDevice(
                    arena,
                    createDevice,
                    D3D_DRIVER_TYPE_WARP,
                    D3D11_CREATE_DEVICE_BGRA_SUPPORT,
                )
                if (created == null) {
                    log.warning("D3D11CreateDevice falló")
                    return null
                }
                val (device, context) = created

                enableMultithread(arena, context)

                val textureOut = arena.allocate(ADDRESS)
                val createTexture = comMethod(
                    device,
                    index = 5,
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
                )
                val desc = textureDesc(arena, width, height)
                val textureHr = createTexture.invokeWithArguments(
                    device,
                    desc,
                    MemorySegment.NULL,
                    textureOut,
                ) as Int
                if (textureHr < 0) {
                    log.warning("CreateTexture2D falló (hr=0x${textureHr.toString(16)})")
                    release(device)
                    release(context)
                    arena.close()
                    return null
                }
                val texture = textureOut.get(ADDRESS, 0L)
                if (texture.address() == 0L) {
                    release(device)
                    release(context)
                    arena.close()
                    return null
                }

                val sharedHandle = getSharedHandle(arena, texture)
                if (sharedHandle == 0L) {
                    log.warning("GetSharedHandle devolvió 0")
                    release(texture)
                    release(device)
                    release(context)
                    arena.close()
                    return null
                }

                val mutexOut = arena.allocate(ADDRESS)
                val mutexHr = queryInterface(texture, IID_IDXGI_KEYED_MUTEX, mutexOut)
                val keyedMutex = if (mutexHr >= 0) mutexOut.get(ADDRESS, 0L) else null
                val acquireSync = keyedMutex?.let {
                    comMethod(it, 8, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT))
                }
                val releaseSync = keyedMutex?.let {
                    comMethod(it, 9, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_LONG))
                }

                log.info(
                    "Textura D3D11 lista ${width}x$height shared=0x${sharedHandle.toString(16)} " +
                        "keyedMutex=${keyedMutex != null}",
                )
                return MpvD3D11Output(
                    arena = arena,
                    width = width,
                    height = height,
                    device = device,
                    context = context,
                    texture = texture,
                    keyedMutex = keyedMutex,
                    sharedHandle = sharedHandle,
                    releaseDevice = comMethod(device, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                    releaseContext = comMethod(context, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                    releaseTexture = comMethod(texture, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS)),
                    releaseMutex = keyedMutex?.let {
                        comMethod(it, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS))
                    },
                    acquireSync = acquireSync,
                    releaseSync = releaseSync,
                )
            } catch (t: Throwable) {
                runCatching { arena.close() }
                throw t
            }
        }

        private fun tryCreateDevice(
            arena: Arena,
            createDevice: MethodHandle,
            driverType: Int,
            flags: Int,
        ): Pair<MemorySegment, MemorySegment>? {
            val deviceOut = arena.allocate(ADDRESS)
            val featureOut = arena.allocate(JAVA_INT)
            val contextOut = arena.allocate(ADDRESS)
            val hr = createDevice.invokeWithArguments(
                MemorySegment.NULL,
                driverType,
                MemorySegment.NULL,
                flags,
                MemorySegment.NULL,
                0,
                D3D11_SDK_VERSION,
                deviceOut,
                featureOut,
                contextOut,
            ) as Int
            if (hr < 0) return null
            val device = deviceOut.get(ADDRESS, 0L)
            val context = contextOut.get(ADDRESS, 0L)
            if (device.address() == 0L || context.address() == 0L) return null
            return device to context
        }

        private fun enableMultithread(arena: Arena, context: MemorySegment) {
            val out = arena.allocate(ADDRESS)
            if (queryInterface(context, IID_ID3D11_MULTITHREAD, out) < 0) return
            val mt = out.get(ADDRESS, 0L)
            if (mt.address() == 0L) return
            runCatching {
                val set = comMethod(mt, 5, FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))
                set.invokeWithArguments(mt, 1)
            }
            release(mt)
        }

        private fun getSharedHandle(arena: Arena, texture: MemorySegment): Long {
            val resourceOut = arena.allocate(ADDRESS)
            if (queryInterface(texture, IID_IDXGI_RESOURCE, resourceOut) < 0) return 0L
            val resource = resourceOut.get(ADDRESS, 0L)
            if (resource.address() == 0L) return 0L
            return try {
                val handleOut = arena.allocate(ADDRESS)
                val get = comMethod(resource, 8, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS))
                val hr = get.invokeWithArguments(resource, handleOut) as Int
                if (hr < 0) 0L else handleOut.get(ADDRESS, 0L).address()
            } finally {
                release(resource)
            }
        }

        private fun queryInterface(obj: MemorySegment, iid: MemorySegment, out: MemorySegment): Int {
            val fn = comMethod(obj, 0, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS))
            return fn.invokeWithArguments(obj, iid, out) as Int
        }

        private fun release(obj: MemorySegment) {
            runCatching {
                val fn = comMethod(obj, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS))
                fn.invokeWithArguments(obj)
            }
        }

        private fun textureDesc(
            arena: Arena,
            width: Int,
            height: Int,
        ): MemorySegment {
            val desc = arena.allocate(44)
            desc.set(JAVA_INT, 0L, width)
            desc.set(JAVA_INT, 4L, height)
            desc.set(JAVA_INT, 8L, 1) // MipLevels
            desc.set(JAVA_INT, 12L, 1) // ArraySize
            desc.set(JAVA_INT, 16L, DXGI_FORMAT_R8G8B8A8_UNORM)
            desc.set(JAVA_INT, 20L, 1) // SampleDesc.Count
            desc.set(JAVA_INT, 24L, 0) // SampleDesc.Quality
            desc.set(JAVA_INT, 28L, D3D11_USAGE_DEFAULT)
            desc.set(JAVA_INT, 32L, D3D11_BIND_RENDER_TARGET or D3D11_BIND_SHADER_RESOURCE)
            desc.set(JAVA_INT, 36L, 0) // CpuAccessFlags
            desc.set(JAVA_INT, 40L, D3D11_RESOURCE_MISC_SHARED_KEYEDMUTEX)
            return desc
        }

        private fun comMethod(obj: MemorySegment, index: Int, descriptor: FunctionDescriptor): MethodHandle {
            val vtable = obj.reinterpret(8).get(ADDRESS, 0L)
            val fn = vtable.reinterpret(((index + 1) * 8).toLong()).get(ADDRESS, index * 8L)
            return linker.downcallHandle(fn, descriptor)
        }

        private fun guid(
            data1: Int,
            data2: Int,
            data3: Int,
            vararg data4: Int,
        ): MemorySegment {
            val arena = Arena.ofShared()
            val seg = arena.allocate(16)
            seg.set(JAVA_INT, 0L, data1)
            seg.set(JAVA_SHORT, 4L, data2.toShort())
            seg.set(JAVA_SHORT, 6L, data3.toShort())
            data4.forEachIndexed { index, value ->
                seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, (8 + index).toLong(), value.toByte())
            }
            // Leaked deliberately: GUIDs live as long as the process, and they
            // are shared by every output instance.
            return seg
        }
    }
}
