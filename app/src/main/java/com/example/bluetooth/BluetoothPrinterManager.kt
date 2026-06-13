package com.example.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.viewmodel.Receipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

sealed class PrintState {
    object Idle : PrintState()
    object Loading : PrintState()
    object Success : PrintState()
    data class Error(val message: String) : PrintState()
}

class BluetoothPrinterManager(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("POS_Printer_Prefs", Context.MODE_PRIVATE)
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _printState = MutableStateFlow<PrintState>(PrintState.Idle)
    val printState = _printState.asStateFlow()

    companion object {
        private const val PRINTER_MAC_KEY = "printer_mac_address"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // UUID standard SPP
    }

    // Mendapatkan printer terpilih
    fun getSelectedPrinterMac(): String? {
        return sharedPrefs.getString(PRINTER_MAC_KEY, null)
    }

    // Menyimpan printer terpilih
    fun saveSelectedPrinter(macAddress: String) {
        sharedPrefs.edit().putString(PRINTER_MAC_KEY, macAddress).apply()
    }

    // Memeriksa apakah izin bluetooth diberikan
    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Di bawah Android 12, izin otomatis diberikan jika dideklarasikan di manifest
        }
    }

    // Mengambil daftar perangkat printer Bluetooth yang sudah dipasangkan (paired)
    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): List<BluetoothDevice> {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            return emptyList()
        }
        if (!hasBluetoothPermission()) {
            return emptyList()
        }

        return try {
            bluetoothAdapter.bondedDevices.toList()
        } catch (e: Exception) {
            Log.e("BluetoothPrinter", "Error getting bonded devices", e)
            emptyList()
        }
    }

    // Melakukan proses pencetakan struk
    @SuppressLint("MissingPermission")
    suspend fun printReceipt(receipt: Receipt): Boolean = withContext(Dispatchers.IO) {
        _printState.value = PrintState.Loading

        val macAddress = getSelectedPrinterMac()
        if (macAddress.isNullOrEmpty()) {
            _printState.value = PrintState.Error("Printer Bluetooth belum dipilih. Silakan pilih printer dari daftar di bawah.")
            return@withContext false
        }

        if (bluetoothAdapter == null) {
            _printState.value = PrintState.Error("Perangkat Anda tidak mendukung Bluetooth.")
            return@withContext false
        }

        if (!bluetoothAdapter.isEnabled) {
            _printState.value = PrintState.Error("Bluetooth mati. Silakan aktifkan Bluetooth ponsel Anda terlebih dahulu.")
            return@withContext false
        }

        if (!hasBluetoothPermission()) {
            _printState.value = PrintState.Error("Izin Bluetooth ditolak. Tolong berikan izin untuk melanjutkan.")
            return@withContext false
        }

        var socket: BluetoothSocket? = null
        var lastException: Exception? = null

        try {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            bluetoothAdapter.cancelDiscovery() // Batalkan discovery agar proses koneksi lancar

            // Metode 1: Mencoba koneksi dengan Secure RFCOMM Socket (SPP_UUID)
            try {
                Log.d("BluetoothPrinter", "Menjalankan Metode 1: Secure RFCOMM Socket")
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            } catch (e1: Exception) {
                lastException = e1
                Log.w("BluetoothPrinter", "Metode 1 gagal, mencoba Metode 2 (Insecure Socket)...", e1)
                try { socket?.close() } catch (ce: Exception) {}

                // Metode 2: Mencoba koneksi dengan Insecure RFCOMM Socket (SPP_UUID)
                try {
                    Log.d("BluetoothPrinter", "Menjalankan Metode 2: Insecure RFCOMM Socket")
                    socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    socket.connect()
                } catch (e2: Exception) {
                    lastException = e2
                    Log.w("BluetoothPrinter", "Metode 2 gagal, mencoba Metode 3 (Reflection Port 1)...", e2)
                    try { socket?.close() } catch (ce: Exception) {}

                    // Metode 3: Menggunakan refleksi Java langsung ke Port 1 (sangat cocok untuk seri MP-58A / MP-58A1)
                    try {
                        Log.d("BluetoothPrinter", "Menjalankan Metode 3: Refleksi Java Port 1")
                        val m = device.javaClass.getMethod("createRfcommSocket", *arrayOf<Class<*>>(Int::class.javaPrimitiveType!!))
                        socket = m.invoke(device, 1) as BluetoothSocket
                        socket.connect()
                    } catch (e3: Exception) {
                        lastException = e3
                        Log.e("BluetoothPrinter", "Semua alur koneksi Bluetooth gagal.", e3)
                        throw e3
                    }
                }
            }

            val outputStream = socket.outputStream

            // Tulis ESC/POS data
            writeEscPosBytes(outputStream, receipt)

            // Memberikan waktu tunda (delay) agar printer MP-58A1 selesai mencetak dari buffer buffer bluetooth
            // sebelum akhirnya koneksi socket kita putuskan dalam blok 'finally'.
            // Ini adalah perbaikan SANGAT PENTING untuk printer mikro budget (seperti MP-58A1/Iware/VSC).
            delay(2000)

            _printState.value = PrintState.Success
            true
        } catch (e: Exception) {
            Log.e("BluetoothPrinter", "Bluetooth connection/printing failure after trying all fallbacks", e)
            val friendlyMsg = when {
                e is IOException && e.message?.contains("Service discover failed", ignoreCase = true) == true -> {
                    "Gagal menemukan layanan printer MP-58A1. Matikan lalu hidupkan kembali Bluetooth HP Anda, kemudian coba lagi."
                }
                e is IOException && e.message?.contains("read failed, socket might closed", ignoreCase = true) == true -> {
                    "Printer menolak koneksi. Silakan Hapus Pasangan (Unpair) perangkat MP-58A1 dari pengaturan Bluetooth HP Anda, lalu pasangkan kembali dengan PIN default (biasanya 0000 atau 1234)."
                }
                else -> "Gagal terhubung dengan MP-58A1. Pastikan printer menyala, baterai cukup, dan tekan ulang tombol cetak. Detail: ${e.localizedMessage}"
            }
            _printState.value = PrintState.Error(friendlyMsg)
            false
        } finally {
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothPrinter", "Gagal menutup socket Bluetooth", e)
            }
        }
    }

    // Translate struk POS menjadi byte stream thermal roll 58mm standar (32 karakter kolom baris)
    private fun writeEscPosBytes(output: java.io.OutputStream, receipt: Receipt) {
        val lineSize = 32
        val charset = java.nio.charset.Charset.forName("GBK") // Sangat umum untuk printer thermal mikro murah (seperti MP-58A1)

        // Perintah standard ESC/POS thermal printer
        val initPrinter = byteArrayOf(0x1B, 0x40)
        val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
        val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)
        val alignRight = byteArrayOf(0x1B, 0x61, 0x02)
        val boldOn = byteArrayOf(0x1B, 0x45, 0x01)
        val boldOff = byteArrayOf(0x1B, 0x45, 0x00)
        val textNormal = byteArrayOf(0x1D, 0x21, 0x00)
        val textDoubleSize = byteArrayOf(0x1D, 0x21, 0x11) // Double Width + Double Height

        // Perintah ESC d n (0x1B 0x64 n): Mencetak semua buffer dan mengumpan kertas n baris
        fun feedLines(n: Byte): ByteArray {
            return byteArrayOf(0x1B, 0x64, n)
        }

        output.write(initPrinter)

        // Helper untuk menulis teks baris dengan CRLF (\r\n) agar printer segera mencetak baris tsb
        fun writeLine(text: String) {
            output.write(text.toByteArray(charset))
        }

        // 1. HEADER TOKO (TENGAH, DUA KALI UP)
        output.write(alignCenter)
        output.write(textDoubleSize)
        output.write(boldOn)
        writeLine("${receipt.storeName}\r\n")
        output.write(boldOff)
        output.write(textNormal)

        writeLine("Ruko Grand Gateway No. 12\r\n")
        writeLine("Telp: 0812-3456-7890\r\n")

        // Separator
        writeLine("--------------------------------\r\n")

        // INFORMASI TRANSAKSI
        output.write(alignLeft)
        writeLine("TGL: ${receipt.transactionDateTime}\r\n")
        writeLine("KSR: UMKM ADMIN\r\n")
        writeLine("--------------------------------\r\n")

        // 2. DAFTAR ITEM BELANJA
        receipt.items.forEach { cartItem ->
            val rawName = cartItem.product.name.uppercase()
            val finalName = if (rawName.length > lineSize) {
                rawName.substring(0, lineSize - 3) + "..."
            } else {
                rawName
            }
            writeLine("$finalName\r\n")

            // Baris qty & harga
            val qtyText = "${cartItem.quantity} x ${formatCompactRupiah(cartItem.product.price)}"
            val subtotalText = formatCompactRupiah(cartItem.subtotal)
            val spaces = lineSize - qtyText.length - subtotalText.length
            val spacing = " ".repeat(spaces.coerceAtLeast(1))

            writeLine("$qtyText$spacing$subtotalText\r\n")
        }

        writeLine("--------------------------------\r\n")

        // 3. RINGKASAN PEMBAYARAN
        val labelTotal = "TOTAL:"
        val valTotal = formatCompactRupiah(receipt.totalAmount)
        val spaceTotal = " ".repeat((lineSize - labelTotal.length - valTotal.length).coerceAtLeast(1))
        output.write(boldOn)
        writeLine("$labelTotal$spaceTotal$valTotal\r\n")
        output.write(boldOff)

        val labelBayar = "TUNAI:"
        val valBayar = formatCompactRupiah(receipt.paymentReceived)
        val spaceBayar = " ".repeat((lineSize - labelBayar.length - valBayar.length).coerceAtLeast(1))
        writeLine("$labelBayar$spaceBayar$valBayar\r\n")

        val labelKembali = "KEMBALIAN:"
        val valKembali = formatCompactRupiah(receipt.changeAmount)
        val spaceKembali = " ".repeat((lineSize - labelKembali.length - valKembali.length).coerceAtLeast(1))
        output.write(boldOn)
        writeLine("$labelKembali$spaceKembali$valKembali\r\n")
        output.write(boldOff)

        writeLine("--------------------------------\r\n")

        // FOOTER
        output.write(alignCenter)
        writeLine("TERIMA KASIH\r\n")
        writeLine("BELANJA KEMBALI DI TOKO KAMI\r\n")
        
        // Memaksa cetak buffer dan mengumpan kertas keluar agar user bisa dengan mudah merobeknya
        output.write(feedLines(4))
        output.flush()
    }

    private fun formatCompactRupiah(value: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        format.maximumFractionDigits = 0
        return format.format(value).replace("Rp", "Rp").replace(" ", "")
    }

    fun resetState() {
        _printState.value = PrintState.Idle
    }
}
