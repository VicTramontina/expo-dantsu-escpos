package com.dantsu.escposprinter.connection.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BluetoothConnections {
    protected BluetoothAdapter bluetoothAdapter;
    
    /**
     * Create a new instance of BluetoothConnections
     */
    public BluetoothConnections() {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }
    
    /**
     * Get a list of bluetooth devices available.
     * @return Return an array of BluetoothConnection instance
     */
    @SuppressLint("MissingPermission")
    @Nullable
    public BluetoothConnection[] getList() {
        if (this.bluetoothAdapter == null) {
            return null;
        }
    
        if(!this.bluetoothAdapter.isEnabled()) {
            return null;
        }
        
        Set<BluetoothDevice> bluetoothDevicesList = this.bluetoothAdapter.getBondedDevices();
        BluetoothConnection[] bluetoothDevices = new BluetoothConnection[bluetoothDevicesList.size()];
    
        if (bluetoothDevicesList.size() > 0) {
            int i = 0;
            for (BluetoothDevice device : bluetoothDevicesList) {
                bluetoothDevices[i++] = new BluetoothConnection(device);
            }
        }
        
        return bluetoothDevices;
    }
    
    /**
     * Get a list of nearby unpaired bluetooth devices.
     * @param context Android context required for broadcast receiver
     * @return Return an array of BluetoothConnection instance for unpaired devices
     */
    @SuppressLint("MissingPermission")
    @Nullable
    public BluetoothConnection[] getUnpairedList(Context context) {
        if (this.bluetoothAdapter == null) {
            return null;
        }
    
        if(!this.bluetoothAdapter.isEnabled()) {
            return null;
        }
        
        final List<BluetoothDevice> discoveredDevices = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        
        BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        boolean alreadyAdded = false;
                        for (BluetoothDevice existing : discoveredDevices) {
                            if (existing.getAddress().equals(device.getAddress())) {
                                alreadyAdded = true;
                                break;
                            }
                        }
                        if (!alreadyAdded) {
                            discoveredDevices.add(device);
                        }
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    latch.countDown();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(discoveryReceiver, filter);
        
        try {
            if (this.bluetoothAdapter.isDiscovering()) {
                this.bluetoothAdapter.cancelDiscovery();
            }
            
            if (this.bluetoothAdapter.startDiscovery()) {
                // Wait up to 15 seconds for discovery to complete
                latch.await(15, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            context.unregisterReceiver(discoveryReceiver);
            if (this.bluetoothAdapter.isDiscovering()) {
                this.bluetoothAdapter.cancelDiscovery();
            }
        }
        
        BluetoothConnection[] unpairedDevices = new BluetoothConnection[discoveredDevices.size()];
        for (int i = 0; i < discoveredDevices.size(); i++) {
            unpairedDevices[i] = new BluetoothConnection(discoveredDevices.get(i));
        }
        
        return unpairedDevices;
    }
}
