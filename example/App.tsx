import {ReactNode, useEffect, useState} from 'react';
import type {BluetoothDevice, UsbDevice, PrinterInfo, TcpDevice, BluetoothConnectionResult} from 'expo-dantsu-escpos';
import ExpoEscposDantsuModule from 'expo-dantsu-escpos';
import {
    Button,
    SafeAreaView,
    ScrollView,
    Text,
    View,
    TextInput,
    TouchableOpacity,
    StyleSheet,
    ActivityIndicator,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';

export default function App() {
    const [btDevices, setBtDevices] = useState<BluetoothDevice[]>([]);
    const [usbDevices, setUsbDevices] = useState<UsbDevice[]>([]);
    const [tcpDevices, setTcpDevices] = useState<TcpDevice[]>([]);
    const [connected, setConnected] = useState(false);
    const [connectionInfo, setConnectionInfo] = useState<BluetoothConnectionResult | null>(null);
    const [selectedDevice, setSelectedDevice] = useState<BluetoothDevice | null>(null);
    const [nameFilter, setNameFilter] = useState('');
    const [text, setText] = useState('<C>Hello from Expo!</C>\n<BR>');
    const [barcode, setBarcode] = useState('123456789012');
    const [qr, setQr] = useState('https://expo.dev');
    const [mm, setMm] = useState('10');
    const [mmPx, setMmPx] = useState<number | null>(null);
    const [printerInfo, setPrinterInfo] = useState<PrinterInfo | null>(null);
    const [tcpAddress, setTcpAddress] = useState('');
    const [tcpPort, setTcpPort] = useState('9100');
    const [escAsterisk, setEscAsterisk] = useState(false);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        // Auto-focus setup or other initialization if needed
    }, []);

    const handleOperation = async (operation: () => Promise<any>) => {
        setLoading(true);
        try {
            await operation();
        } catch (error) {
            console.error("Operation failed:", error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <SafeAreaView style={styles.container}>
            <StatusBar style="dark" backgroundColor="#f5f6fa" />
            <ScrollView style={styles.scrollView} contentContainerStyle={styles.contentContainer}>
                <Text style={styles.header}>Thermal Printer Test</Text>

                <Group name="Enhanced Bluetooth Printers">
                    <SectionHeader title="Device Discovery" />
                    <View style={styles.actionRow}>
                        <StyledButton
                            title="Scan Bonded Only"
                            icon="📱"
                            onPress={() => handleOperation(async () => {
                                const list = await ExpoEscposDantsuModule.getBluetoothDevices({ 
                                    includeBondedOnly: true 
                                });
                                setBtDevices(list);
                            })}
                            loading={loading}
                            style={styles.actionButton}
                        />
                        <StyledButton
                            title="Full Discovery"
                            icon="🔍"
                            onPress={() => handleOperation(async () => {
                                const list = await ExpoEscposDantsuModule.getBluetoothDevices({ 
                                    scanMillis: 6000,
                                    includeRssi: true,
                                    nameRegex: nameFilter || undefined
                                });
                                setBtDevices(list);
                            })}
                            loading={loading}
                            style={styles.actionButton}
                        />
                    </View>
                    
                    <TextInput
                        style={styles.input}
                        placeholder="Filter by name (optional, regex supported)"
                        value={nameFilter}
                        onChangeText={setNameFilter}
                        placeholderTextColor="#a0a0a0"
                    />

                    <SectionHeader title="Available Devices" />
                    {btDevices.map(device => (
                        <TouchableOpacity
                            key={device.address}
                            style={[
                                styles.deviceCard,
                                selectedDevice?.address === device.address && styles.deviceCardSelected
                            ]}
                            onPress={() => setSelectedDevice(device)}
                        >
                            <View style={styles.deviceInfo}>
                                <Text style={styles.deviceName}>
                                    {device.deviceName || 'Unknown Device'}
                                </Text>
                                <Text style={styles.deviceAddress}>{device.address}</Text>
                                <View style={styles.deviceMeta}>
                                    <Text style={[styles.deviceTag, device.bonded ? styles.bondedTag : styles.unbondedTag]}>
                                        {device.bonded ? '📱 Bonded' : '📡 Discovered'}
                                    </Text>
                                    {device.rssi && (
                                        <Text style={styles.deviceTag}>
                                            📶 {device.rssi}dBm
                                        </Text>
                                    )}
                                    <Text style={styles.deviceTag}>
                                        📍 {device.source}
                                    </Text>
                                </View>
                            </View>
                        </TouchableOpacity>
                    ))}
                    
                    {selectedDevice && (
                        <View>
                            <SectionHeader title="Connect to Selected Device" />
                            <View style={styles.selectedDeviceCard}>
                                <Text style={styles.selectedDeviceText}>
                                    Selected: {selectedDevice.deviceName || 'Unknown'} ({selectedDevice.address})
                                </Text>
                            </View>
                            <StyledButton
                                title={`Connect with Insecure SPP ${selectedDevice.bonded ? '(Fallback)' : '(Primary)'}`}
                                icon="🔗"
                                onPress={() => handleOperation(async () => {
                                    const result = await ExpoEscposDantsuModule.connectBluetooth({
                                        address: selectedDevice.address,
                                        nameHint: selectedDevice.deviceName || undefined,
                                        preferInsecureIfUnbonded: true,
                                        allowSecureFallback: true,
                                        timeoutMs: 15000
                                    });
                                    setConnectionInfo(result);
                                    setConnected(true);
                                    console.log('Connected with mode:', result.connectionMode);
                                })}
                                loading={loading}
                            />
                        </View>
                    )}
                    
                    {connectionInfo && (
                        <View style={styles.connectionCard}>
                            <Text style={styles.connectionTitle}>Connection Status</Text>
                            <View style={styles.infoRow}>
                                <Text style={styles.infoLabel}>Mode:</Text>
                                <Text style={[
                                    styles.infoValue, 
                                    connectionInfo.connectionMode === 'insecure' 
                                        ? styles.insecureMode 
                                        : styles.secureMode
                                ]}>
                                    {connectionInfo.connectionMode.toUpperCase()}
                                    {connectionInfo.connectionMode === 'insecure' ? ' 🔓' : ' 🔒'}
                                </Text>
                            </View>
                            <View style={styles.infoRow}>
                                <Text style={styles.infoLabel}>DPI:</Text>
                                <Text style={styles.infoValue}>{connectionInfo.dpi}</Text>
                            </View>
                            <View style={styles.infoRow}>
                                <Text style={styles.infoLabel}>Width:</Text>
                                <Text style={styles.infoValue}>{connectionInfo.widthMM}mm</Text>
                            </View>
                        </View>
                    )}

                    {btDevices.length === 0 && (
                        <InfoText text="No devices found. Tap scan buttons to discover devices." />
                    )}
                </Group>

                <Group name="USB Printers">
                    <StyledButton
                        title="List USB Devices"
                        icon="🔌"
                        onPress={() => handleOperation(async () => {
                            const list = await ExpoEscposDantsuModule.getUsbDevices();
                            console.log("USB Devices:", list);
                            setUsbDevices(list);
                        })}
                        loading={loading}
                    />
                    {usbDevices.map(device => (
                        <StyledButton
                            key={`${device.vendorId}-${device.productId}`}
                            title={`Connect to ${device.deviceName}`}
                            icon="📶"
                            onPress={() => handleOperation(async () => {
                                try {
                                    await ExpoEscposDantsuModule.connectUsb(device.vendorId, device.productId);
                                    setConnected(true);
                                } catch (error) {
                                    console.error("USB Connection Error:", error);
                                }
                            })}
                            loading={loading}
                        />
                    ))}
                    {usbDevices.length === 0 && (
                        <InfoText text="No USB devices found. Tap 'List USB Devices' to scan." />
                    )}
                </Group>

                <Group name="TCP Printers">
                    <StyledButton
                        title="List TCP Devices"
                        icon="🔍"
                        onPress={() => handleOperation(async () => {
                            const list = await ExpoEscposDantsuModule.getTcpDevices();
                            console.log("TCP Devices:", list);
                            setTcpDevices(list);
                        })}
                        loading={loading}
                    />
                    {tcpDevices.map(device => (
                        <StyledButton
                            key={`${device.address}-${device.port}`}
                            title={`Connect to ${device.address}:${device.port}`}
                            icon="📶"
                            onPress={() => handleOperation(async () => {
                                try {
                                    await ExpoEscposDantsuModule.connectTcp(device.address, device.port, 2000);
                                    setConnected(true);
                                } catch (error) {
                                    console.error("TCP Connection Error:", error);
                                }
                            })}
                            loading={loading}
                        />
                    ))}
                    {tcpDevices.length === 0 && (
                        <InfoText text="No TCP devices found. Tap 'List TCP Devices' to scan." />
                    )}

                    <SectionHeader title="Manual TCP Connection" />
                    <View style={styles.inputRow}>
                        <TextInput
                            style={[styles.input, styles.addressInput]}
                            placeholder="Address (e.g. 192.168.1.100)"
                            value={tcpAddress}
                            onChangeText={setTcpAddress}
                            placeholderTextColor="#a0a0a0"
                        />
                        <TextInput
                            style={[styles.input, styles.portInput]}
                            placeholder="Port"
                            keyboardType="numeric"
                            value={tcpPort}
                            onChangeText={setTcpPort}
                            placeholderTextColor="#a0a0a0"
                        />
                    </View>
                    <StyledButton
                        title="Connect Manually"
                        icon="🖨️"
                        onPress={() => handleOperation(async () => {
                            if (!tcpAddress || !tcpPort) return;
                            await ExpoEscposDantsuModule.connectTcp(tcpAddress, Number(tcpPort), 2000);
                            setConnected(true);
                        })}
                        loading={loading}
                        disabled={!tcpAddress || !tcpPort}
                    />
                </Group>

                {connected && (
                    <Group name="Printer Actions">
                        <SectionHeader title="Print Text" />
                        <TextInput
                            style={[styles.input, styles.multilineInput]}
                            value={text}
                            onChangeText={setText}
                            placeholder="ESC/POS formatted text"
                            placeholderTextColor="#a0a0a0"
                            multiline={true}
                        />
                        <StyledButton
                            title="Print Text"
                            icon="📄"
                            onPress={() => handleOperation(async () =>
                                ExpoEscposDantsuModule.printText(text)
                            )}
                            loading={loading}
                        />

                        <SectionHeader title="Print Barcode" />
                        <TextInput
                            style={styles.input}
                            value={barcode}
                            onChangeText={setBarcode}
                            placeholder="Barcode data"
                            placeholderTextColor="#a0a0a0"
                        />
                        <StyledButton
                            title="Print Barcode"
                            icon="📊"
                            onPress={() => handleOperation(async () =>
                                ExpoEscposDantsuModule.printBarcode(barcode)
                            )}
                            loading={loading}
                        />

                        <SectionHeader title="Print QR Code" />
                        <TextInput
                            style={styles.input}
                            value={qr}
                            onChangeText={setQr}
                            placeholder="QR code data"
                            placeholderTextColor="#a0a0a0"
                        />
                        <StyledButton
                            title="Print QR Code"
                            icon="📱"
                            onPress={() => handleOperation(async () =>
                                ExpoEscposDantsuModule.printQRCode(qr)
                            )}
                            loading={loading}
                        />

                        <SectionHeader title="Printer Controls" />
                        <View style={styles.actionRow}>
                            <StyledButton
                                title="Feed 5mm"
                                icon="⬇️"
                                onPress={() => handleOperation(async () =>
                                    ExpoEscposDantsuModule.feedPaper(5)
                                )}
                                loading={loading}
                                style={styles.actionButton}
                            />
                            <StyledButton
                                title="Cut Paper"
                                icon="✂️"
                                onPress={() => handleOperation(async () =>
                                    ExpoEscposDantsuModule.cutPaper()
                                )}
                                loading={loading}
                                style={styles.actionButton}
                            />
                        </View>
                        <View style={styles.actionRow}>
                            <StyledButton
                                title="Cash Drawer"
                                icon="💰"
                                onPress={() => handleOperation(async () =>
                                    ExpoEscposDantsuModule.openCashDrawer()
                                )}
                                loading={loading}
                                style={styles.actionButton}
                            />
                            <StyledButton
                                title={escAsterisk ? 'Disable ESC *' : 'Enable ESC *'}
                                icon="⚙️"
                                onPress={() => handleOperation(async () => {
                                    await ExpoEscposDantsuModule.useEscAsteriskCommand(!escAsterisk);
                                    setEscAsterisk(!escAsterisk);
                                })}
                                loading={loading}
                                style={styles.actionButton}
                            />
                        </View>

                        <SectionHeader title="Printer Information" />
                        <StyledButton
                            title="Get Printer Info"
                            icon="ℹ️"
                            onPress={() => handleOperation(async () => {
                                const info = await ExpoEscposDantsuModule.getPrinterInfo();
                                setPrinterInfo(info);
                            })}
                            loading={loading}
                        />
                        {printerInfo && (
                            <View style={styles.infoCard}>
                                {Object.entries(printerInfo).map(([key, value]) => (
                                    <View key={key} style={styles.infoRow}>
                                        <Text style={styles.infoLabel}>{key}:</Text>
                                        <Text style={styles.infoValue}>{String(value)}</Text>
                                    </View>
                                ))}
                            </View>
                        )}

                        <SectionHeader title="Measurement Conversion" />
                        <View style={styles.conversionContainer}>
                            <TextInput
                                style={[styles.input, styles.conversionInput]}
                                placeholder="mm"
                                keyboardType="numeric"
                                value={mm}
                                onChangeText={setMm}
                                placeholderTextColor="#a0a0a0"
                            />
                            <StyledButton
                                title="mm → px"
                                icon="📏"
                                onPress={() => handleOperation(async () => {
                                    const px = await ExpoEscposDantsuModule.mmToPx(Number(mm));
                                    setMmPx(px);
                                })}
                                loading={loading}
                                style={styles.mmButton}
                            />
                        </View>
                        {mmPx !== null && (
                            <View style={styles.resultBox}>
                                <Text style={styles.resultText}>{mm}mm = {mmPx}px</Text>
                            </View>
                        )}

                        <View style={styles.disconnectContainer}>
                            <StyledButton
                                title="Disconnect"
                                icon="🔌"
                                onPress={() => handleOperation(async () => {
                                    await ExpoEscposDantsuModule.disconnect();
                                    setConnected(false);
                                    setConnectionInfo(null);
                                    setSelectedDevice(null);
                                })}
                                loading={loading}
                                style={styles.disconnectButton}
                            />
                        </View>
                    </Group>
                )}
            </ScrollView>
        </SafeAreaView>
    );
}

function Group(props: { name: string; children: ReactNode }) {
    return (
        <View style={styles.group}>
            <Text style={styles.groupHeader}>{props.name}</Text>
            {props.children}
        </View>
    );
}

function SectionHeader({ title }: { title: string }) {
    return (
        <View style={styles.sectionHeaderContainer}>
            <Text style={styles.sectionHeader}>{title}</Text>
        </View>
    );
}

function InfoText({ text }: { text: string }) {
    return <Text style={styles.infoText}>{text}</Text>;
}

function StyledButton({
    title,
    onPress,
    loading = false,
    disabled = false,
    style = {},
    icon = ""
}: {
    title: string;
    onPress: () => void;
    loading?: boolean;
    disabled?: boolean;
    style?: any;
    icon?: string;
}) {
    return (
        <TouchableOpacity
            onPress={onPress}
            style={[
                styles.button,
                style,
                disabled && styles.buttonDisabled
            ]}
            disabled={disabled || loading}
        >
            {loading ? (
                <ActivityIndicator color="#fff" />
            ) : (
                <View style={styles.buttonContent}>
                    {icon && <Text style={styles.buttonIcon}>{icon}</Text>}
                    <Text style={styles.buttonText}>{title}</Text>
                </View>
            )}
        </TouchableOpacity>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: '#f5f6fa',
    },
    scrollView: {
        flex: 1,
    },
    contentContainer: {
        paddingBottom: 30,
    },
    header: {
        fontSize: 32,
        margin: 24,
        fontWeight: 'bold',
        textAlign: 'center',
        color: '#3f51b5',
        letterSpacing: 1,
    },
    groupHeader: {
        fontSize: 22,
        marginBottom: 16,
        fontWeight: '700',
        color: '#3f51b5',
        letterSpacing: 0.5,
    },
    group: {
        marginHorizontal: 16,
        marginVertical: 12,
        backgroundColor: '#fff',
        borderRadius: 16,
        padding: 20,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.08,
        shadowRadius: 8,
        elevation: 4,
    },
    input: {
        borderWidth: 1,
        borderColor: '#e0e0e0',
        backgroundColor: '#ffffff',
        padding: 14,
        marginVertical: 8,
        borderRadius: 10,
        fontSize: 16,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.05,
        shadowRadius: 2,
        elevation: 1,
    },
    multilineInput: {
        height: 100,
        textAlignVertical: 'top',
    },
    button: {
        backgroundColor: '#4C6EF5',
        paddingVertical: 14,
        paddingHorizontal: 20,
        borderRadius: 10,
        marginVertical: 8,
        elevation: 2,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
    },
    buttonDisabled: {
        backgroundColor: '#b0bec5',
    },
    buttonText: {
        color: '#fff',
        fontSize: 16,
        fontWeight: '600',
        textAlign: 'center',
    },
    buttonIcon: {
        fontSize: 18,
        marginRight: 8,
    },
    buttonContent: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
    },
    inputRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        marginBottom: 8,
    },
    addressInput: {
        flex: 3,
        marginRight: 8,
    },
    portInput: {
        flex: 1,
    },
    actionRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
    },
    actionButton: {
        flex: 1,
        margin: 4,
    },
    infoText: {
        color: '#78909c',
        fontStyle: 'italic',
        textAlign: 'center',
        marginVertical: 10,
    },
    sectionHeaderContainer: {
        marginTop: 16,
        marginBottom: 8,
        borderBottomWidth: 1,
        borderBottomColor: '#e0e0e0',
    },
    sectionHeader: {
        fontSize: 18,
        fontWeight: '600',
        color: '#455a64',
        marginBottom: 8,
    },
    conversionContainer: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
    },
    conversionInput: {
        flex: 1,
        marginRight: 10,
    },
    mmButton: {
        flex: 1,
    },
    resultBox: {
        backgroundColor: '#edf2ff',
        padding: 14,
        borderRadius: 8,
        marginVertical: 10,
        borderLeftWidth: 4,
        borderLeftColor: '#4C6EF5',
    },
    resultText: {
        fontSize: 16,
        color: '#3f51b5',
        fontWeight: '600',
    },
    disconnectContainer: {
        marginTop: 20,
        borderTopWidth: 1,
        borderTopColor: '#e0e0e0',
        paddingTop: 20,
    },
    disconnectButton: {
        backgroundColor: '#ff5252',
    },
    infoCard: {
        backgroundColor: '#f5f5f5',
        borderRadius: 8,
        padding: 12,
        marginVertical: 8,
    },
    infoRow: {
        flexDirection: 'row',
        marginVertical: 4,
    },
    infoLabel: {
        fontWeight: '600',
        width: 120,
        color: '#455a64',
    },
    infoValue: {
        flex: 1,
        color: '#37474f',
    },
    deviceCard: {
        backgroundColor: '#f8f9ff',
        borderWidth: 1,
        borderColor: '#e0e6ff',
        borderRadius: 12,
        padding: 16,
        marginVertical: 6,
        elevation: 1,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 1 },
        shadowOpacity: 0.05,
        shadowRadius: 3,
    },
    deviceCardSelected: {
        backgroundColor: '#e8f0ff',
        borderColor: '#4C6EF5',
        borderWidth: 2,
    },
    deviceInfo: {
        flex: 1,
    },
    deviceName: {
        fontSize: 16,
        fontWeight: '600',
        color: '#2d3748',
        marginBottom: 4,
    },
    deviceAddress: {
        fontSize: 14,
        color: '#718096',
        marginBottom: 8,
        fontFamily: 'monospace',
    },
    deviceMeta: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: 8,
    },
    deviceTag: {
        fontSize: 12,
        paddingHorizontal: 8,
        paddingVertical: 4,
        borderRadius: 12,
        backgroundColor: '#f7fafc',
        color: '#4a5568',
        overflow: 'hidden',
    },
    bondedTag: {
        backgroundColor: '#c6f6d5',
        color: '#22543d',
    },
    unbondedTag: {
        backgroundColor: '#fed7d7',
        color: '#742a2a',
    },
    selectedDeviceCard: {
        backgroundColor: '#bee3f8',
        borderWidth: 1,
        borderColor: '#4299e1',
        borderRadius: 8,
        padding: 12,
        marginVertical: 8,
    },
    selectedDeviceText: {
        fontSize: 14,
        fontWeight: '600',
        color: '#2b6cb0',
    },
    connectionCard: {
        backgroundColor: '#f0fff4',
        borderWidth: 2,
        borderColor: '#68d391',
        borderRadius: 12,
        padding: 16,
        marginVertical: 8,
    },
    connectionTitle: {
        fontSize: 16,
        fontWeight: '700',
        color: '#22543d',
        marginBottom: 12,
        textAlign: 'center',
    },
    insecureMode: {
        color: '#e53e3e',
        fontWeight: '700',
    },
    secureMode: {
        color: '#38a169',
        fontWeight: '700',
    },
});
