import React, {useEffect, useState} from 'react';
import {
    SafeAreaView,
    StyleSheet,
    View,
    Text,
    FlatList,
    TouchableOpacity,
    TextInput,
    Button,
    Alert,
} from 'react-native';
import Escpos from 'expo-dantsu-escpos';

const TABS = ['Bluetooth', 'USB', 'TCP'] as const;
type Tab = typeof TABS[number];

export default function App() {
    const [tab, setTab] = useState<Tab>('Bluetooth');
    const [bluetoothDevices, setBluetoothDevices] = useState<Array<{ name: string; address: string }>>([]);
    const [usbDevices, setUsbDevices] = useState<Array<{ name: string; vendorId: number; productId: number }>>([]);
    const [selectedBt, setSelectedBt] = useState<string | null>(null);
    const [selectedUsb, setSelectedUsb] = useState<{ vendorId: number; productId: number } | null>(null);
    const [tcpAddress, setTcpAddress] = useState('192.168.0.100');
    const [tcpPort, setTcpPort] = useState('9100');
    const [connected, setConnected] = useState(false);

    useEffect(() => {
        if (tab === 'Bluetooth') {
            Escpos.getBluetoothDevices().then(setBluetoothDevices).catch(console.error);
        } else if (tab === 'USB') {
            Escpos.getUSBDevices().then(setUsbDevices).catch(console.error);
        }
    }, [tab]);

    const connect = async () => {
        try {
            if (tab === 'Bluetooth' && selectedBt) {
                await Escpos.connectBluetooth(selectedBt, 203, 80, 48);
            } else if (tab === 'USB' && selectedUsb) {
                const {vendorId, productId} = selectedUsb;
                await Escpos.connectUSB(vendorId, productId, 203, 80, 48);
            } else if (tab === 'TCP') {
                await Escpos.connectTCP(tcpAddress, parseInt(tcpPort), 203, 80, 48);
            }
            setConnected(true);
        } catch (e: any) {
            Alert.alert('Connection Error', e.message || String(e));
        }
    };

    const disconnect = async () => {
        try {
            await Escpos.disconnectPrinter();
        } catch {}
        setConnected(false);
    };

    const printTest = async (method: 'printFormattedText' | 'printFormattedTextAndCut' | 'printFormattedTextAndOpenCashBox') => {
        if (!connected) {
            Alert.alert('Not connected', 'Please connect to a printer first');
            return;
        }
        const sampleReceipt =
            "[C]<u><font size='big'>ORDER N°045</font></u>\n" +
            "[L]\n" +
            "[C]================================\n" +
            "[L]\n" +
            "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99e\n" +
            "[L] Size : S\n" +
            "[L]\n" +
            "[L]<b>AWESOME HAT</b>[R]24.99e\n" +
            "[L] Size : 57/58\n" +
            "[L]\n" +
            "[C]--------------------------------\n" +
            "[L]TOTAL PRICE :[R]34.98e\n" +
            "[L]TAX :[R]4.23e\n" +
            "[L]\n" +
            "[C]================================\n" +
            "[L]\n" +
            "[L]<font size='tall'>Customer :</font>\n" +
            "[L]Raymond DUPONT\n" +
            "[L]5 rue des girafes\n" +
            "[L]31547 PERPETES\n" +
            "[L]Tel : +33801201456\n" +
            "[L]\n" +
            "[C]<barcode type='ean13' height='10'>831254784551</barcode>\n" +
            "<qrcode size='20'>https://dantsu.com/</qrcode>\n" +
            "[L]\n" +
            "[L]\n" +
            "[L]\n" +
            "[L]\n";
        try {
            if (method === 'printFormattedText') {
                await Escpos.printFormattedText(sampleReceipt, 100);
            } else if (method === 'printFormattedTextAndCut') {
                await Escpos.printFormattedTextAndCut(sampleReceipt, 100);
            } else if (method === 'printFormattedTextAndOpenCashBox') {
                await Escpos.printFormattedTextAndOpenCashBox(sampleReceipt, 100);
            }
        } catch (e: any) {
            Alert.alert('Print Error', e.message || String(e));
        }
    };

    return (
        <SafeAreaView style={styles.container}>
            <View style={styles.tabBar}>
                {TABS.map((t) => (
                    <TouchableOpacity key={t} style={[styles.tab, tab === t && styles.activeTab]} onPress={() => {
                        setTab(t);
                        setConnected(false);
                    }}>
                        <Text style={styles.tabText}>{t}</Text>
                    </TouchableOpacity>
                ))}
            </View>
            <View style={styles.content}>
                {tab === 'Bluetooth' && (
                    <FlatList
                        contentContainerStyle={styles.contentPadding}
                        data={bluetoothDevices}
                        keyExtractor={(item) => item.address}
                        ListHeaderComponent={() => (
                            <Text style={styles.title}>Bluetooth Devices</Text>
                        )}
                        renderItem={({item}) => (
                            <TouchableOpacity
                                style={[styles.item, selectedBt === item.address && styles.selectedItem]}
                                onPress={() => setSelectedBt(item.address)}
                            >
                                <Text>{item.name}</Text>
                                <Text>{item.address}</Text>
                            </TouchableOpacity>
                        )}
                        ListFooterComponent={() => (
                            <View>
                                <Button title="Connect" onPress={connect} disabled={!selectedBt} />
                                {connected && (
                                    <View style={styles.printButtons}>
                                        <Text style={styles.title}>Print Methods</Text>
                                        <Button title="Print Text" onPress={() => printTest('printFormattedText')} />
                                        <Button title="Print & Cut" onPress={() => printTest('printFormattedTextAndCut')} />
                                        <Button title="Print & Open Cash Box" onPress={() => printTest('printFormattedTextAndOpenCashBox')} />
                                        <View style={{ height: 8 }} />
                                        <Button title="Disconnect" color="#b00" onPress={disconnect} />
                                    </View>
                                )}
                            </View>
                        )}
                    />
                )}
                {tab === 'USB' && (
                    <FlatList
                        contentContainerStyle={styles.contentPadding}
                        data={usbDevices}
                        keyExtractor={(item) => `${item.vendorId}:${item.productId}`}
                        ListHeaderComponent={() => (
                            <Text style={styles.title}>USB Devices</Text>
                        )}
                        renderItem={({item}) => (
                            <TouchableOpacity
                                style={[styles.item, selectedUsb?.vendorId === item.vendorId && selectedUsb?.productId === item.productId && styles.selectedItem]}
                                onPress={() => setSelectedUsb({vendorId: item.vendorId, productId: item.productId})}
                            >
                                <Text>{item.name}</Text>
                                <Text>VID:{item.vendorId} PID:{item.productId}</Text>
                            </TouchableOpacity>
                        )}
                        ListFooterComponent={() => (
                            <View>
                                <Button title="Connect" onPress={connect} disabled={!selectedUsb} />
                                {connected && (
                                    <View style={styles.printButtons}>
                                        <Text style={styles.title}>Print Methods</Text>
                                        <Button title="Print Text" onPress={() => printTest('printFormattedText')} />
                                        <Button title="Print & Cut" onPress={() => printTest('printFormattedTextAndCut')} />
                                        <Button title="Print & Open Cash Box" onPress={() => printTest('printFormattedTextAndOpenCashBox')} />
                                        <View style={{ height: 8 }} />
                                        <Button title="Disconnect" color="#b00" onPress={disconnect} />
                                    </View>
                                )}
                            </View>
                        )}
                    />
                )}
                {tab === 'TCP' && (
                    <View style={styles.contentPadding}>
                        <Text style={styles.title}>TCP Connection</Text>
                        <TextInput
                            style={styles.input}
                            value={tcpAddress}
                            onChangeText={setTcpAddress}
                            placeholder="Address"
                        />
                        <TextInput
                            style={styles.input}
                            value={tcpPort}
                            onChangeText={setTcpPort}
                            placeholder="Port"
                            keyboardType="numeric"
                        />
                        <Button title="Connect" onPress={connect} />
                        {connected && (
                            <View style={styles.printButtons}>
                                <Text style={styles.title}>Print Methods</Text>
                                <Button title="Print Text" onPress={() => printTest('printFormattedText')} />
                                <Button title="Print & Cut" onPress={() => printTest('printFormattedTextAndCut')} />
                                <Button title="Print & Open Cash Box" onPress={() => printTest('printFormattedTextAndOpenCashBox')} />
                                <View style={{ height: 8 }} />
                                <Button title="Disconnect" color="#b00" onPress={disconnect} />
                            </View>
                        )}
                    </View>
                )}
            </View>
        </SafeAreaView>
    );
}

const styles = StyleSheet.create({
    container: {flex: 1, marginTop: 50},
    tabBar: {flexDirection: 'row'},
    tab: {flex: 1, padding: 12, alignItems: 'center'},
    activeTab: {borderBottomWidth: 2},
    tabText: {fontSize: 16},
    content: {flex: 1},
    contentPadding: {padding: 16},
    title: {fontSize: 18, fontWeight: 'bold', marginVertical: 8},
    item: {padding: 12, borderWidth: 1, borderColor: '#ddd', borderRadius: 4, marginVertical: 4},
    selectedItem: {backgroundColor: '#eef'},
    input: {borderWidth: 1, borderColor: '#ccc', borderRadius: 4, padding: 8, marginVertical: 4},
    printButtons: {marginTop: 16},
});
