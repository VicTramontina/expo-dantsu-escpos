import {ReactNode, useEffect, useState} from 'react';
import type {BluetoothDevice, UsbDevice, PrinterInfo} from 'expo-dantsu-escpos';
import ExpoEscposDantsuModule from 'expo-dantsu-escpos';
import {
    Button,
    SafeAreaView,
    ScrollView,
    Text,
    View,
    TextInput,
} from 'react-native';
import {useEvent} from "expo";

export default function App() {
    const [btDevices, setBtDevices] = useState<BluetoothDevice[]>([]);
    const [usbDevices, setUsbDevices] = useState<UsbDevice[]>([]);
    const [connected, setConnected] = useState(false);
    const [text, setText] = useState('<C>Hello from Expo!</C>\n<BR>');
    const [barcode, setBarcode] = useState('123456789012');
    const [qr, setQr] = useState('https://expo.dev');
    const [mm, setMm] = useState('10');
    const [mmPx, setMmPx] = useState<number | null>(null);
    const [printerInfo, setPrinterInfo] = useState<PrinterInfo | null>(null);
    const [tcpAddress, setTcpAddress] = useState('');
    const [tcpPort, setTcpPort] = useState('9100');
    const [escAsterisk, setEscAsterisk] = useState(false);

    useEffect(() => {

    })

    return (
        <SafeAreaView style={styles.container}>
            <ScrollView style={styles.container}>
                <Text style={styles.header}>Thermal Printer Test</Text>

                <Group name="Bluetooth Printers">
                    <Button
                        title="List Paired Devices"
                        onPress={async () => {
                            const list = await ExpoEscposDantsuModule.getBluetoothDevices();
                            setBtDevices(list);
                        }}
                    />
                    {btDevices.map(device => (
                        <Button
                            key={device.address}
                            title={`Connect ${device.name}`}
                            onPress={async () => {
                                await ExpoEscposDantsuModule.connectBluetooth(device.address);
                                setConnected(true);
                            }}
                        />
                    ))}
                </Group>

                <Group name="USB Printers">
                    <Button
                        title="List USB Devices"
                        onPress={async () => {
                            const list = await ExpoEscposDantsuModule.getUsbDevices();
                            setUsbDevices(list);
                        }}
                    />
                    {usbDevices.map(device => (
                        <Button
                            key={`${device.vendorId}-${device.productId}`}
                            title={`Connect ${device.name}`}
                            onPress={async () => {
                                await ExpoEscposDantsuModule.connectUsb(device.vendorId, device.productId);
                                setConnected(true);
                            }}
                        />
                    ))}
                </Group>

                <Group name="TCP Printer">
                    <TextInput
                        style={styles.input}
                        placeholder="Address"
                        value={tcpAddress}
                        onChangeText={setTcpAddress}
                    />
                    <TextInput
                        style={styles.input}
                        placeholder="Port"
                        keyboardType="numeric"
                        value={tcpPort}
                        onChangeText={setTcpPort}
                    />
                    <Button
                        title="Connect"
                        onPress={async () => {
                            if (!tcpAddress || !tcpPort) return;
                            await ExpoEscposDantsuModule.connectTcp(tcpAddress, Number(tcpPort), 2000);
                            setConnected(true);
                        }}
                    />
                </Group>

                {connected && (
                    <Group name="Printer Actions">
                        <TextInput
                            style={styles.input}
                            value={text}
                            onChangeText={setText}
                            placeholder="ESC/POS formatted text"
                        />
                        <Button title="Print Text" onPress={async () => ExpoEscposDantsuModule.printText(text)}/>

                        <TextInput
                            style={styles.input}
                            value={barcode}
                            onChangeText={setBarcode}
                            placeholder="Barcode data"
                        />
                        <Button title="Print Barcode"
                                onPress={async () => ExpoEscposDantsuModule.printBarcode(barcode)}/>

                        <TextInput
                            style={styles.input}
                            value={qr}
                            onChangeText={setQr}
                            placeholder="QR code data"
                        />
                        <Button title="Print QR Code" onPress={async () => ExpoEscposDantsuModule.printQRCode(qr)}/>

                        <Button title="Feed 5mm" onPress={async () => ExpoEscposDantsuModule.feedPaper(5)}/>
                        <Button title="Cut Paper" onPress={async () => ExpoEscposDantsuModule.cutPaper()}/>
                        <Button title="Open Cash Drawer" onPress={async () => ExpoEscposDantsuModule.openCashDrawer()}/>
                        <Button
                            title={escAsterisk ? 'Disable ESC *' : 'Enable ESC *'}
                            onPress={async () => {
                                await ExpoEscposDantsuModule.useEscAsteriskCommand(!escAsterisk);
                                setEscAsterisk(!escAsterisk);
                            }}
                        />

                        <Button
                            title="Get Printer Info"
                            onPress={async () => {
                                const info = await ExpoEscposDantsuModule.getPrinterInfo();
                                setPrinterInfo(info);
                            }}
                        />
                        {printerInfo && <Text>{JSON.stringify(printerInfo)}</Text>}

                        <View style={{flexDirection: 'row', alignItems: 'center'}}>
                            <TextInput
                                style={[styles.input, {flex: 1}]}
                                placeholder="mm"
                                keyboardType="numeric"
                                value={mm}
                                onChangeText={setMm}
                            />
                            <Button
                                title="mm -> px"
                                onPress={async () => {
                                    const px = await ExpoEscposDantsuModule.mmToPx(Number(mm));
                                    setMmPx(px);
                                }}
                            />
                        </View>
                        {mmPx !== null && <Text>{mm}mm = {mmPx}px</Text>}

                        <Button
                            title="Disconnect"
                            onPress={async () => {
                                await ExpoEscposDantsuModule.disconnect();
                                setConnected(false);
                            }}
                        />
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

const styles = {
    header: {
        fontSize: 30,
        margin: 20,
    },
    groupHeader: {
        fontSize: 20,
        marginBottom: 20,
    },
    group: {
        margin: 20,
        backgroundColor: '#fff',
        borderRadius: 10,
        padding: 20,
    },
    container: {
        flex: 1,
        backgroundColor: '#eee',
    },
    input: {
        borderWidth: 1,
        borderColor: '#ccc',
        padding: 8,
        marginVertical: 8,
    },
    view: {
        flex: 1,
        height: 200,
    },
};
