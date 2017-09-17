# BlueNodes
Bluetooth Smart Home Appliance

BlueNodes is a Smart Home system. Each BlueNode is a small box containing a Bluetooth Low Energy chip, a MOSFET switch, a current measurement sensor and a power supply. Together they establish a mesh-network of Bluetooth® devices and broadcast values to their neighbours. BlueNode devices are inserted in power supply lines to control and measure the power consumption of the endpoint. Android devices since version 4.4 with an installed BlueNodes Controller application are able to connect to the closest BlueNode and exchange information with any node in the network. Every node is reachable in the network as long as a broadcasting path exists to the addressed node. The number of nodes is nearly unlimited. A node can be mounted inside wall sockets, lamp stands, junction boxes and light switches. BlueNode devices can switch or dim loads while supplying continuous information about power consumption of each node in the network. You can start your Smart Home with just one BlueNode.

== Usage
The BlueNodes system consists of hardware, firmware and android software.

=== Prerequisites
==== Hardware
To read and modify schematics and pcb layout, you need to install gEDA (http://www.geda-project.org/) from: http://wiki.geda-project.org/geda:download. You have to copy the gEDA folder of this repo over your gEDA installation to add symbols and footprints that are added for this project. You have to add the bluenodes directory to the gEDA settings or copy footprints (*.fp) and symbols (*.sym) to an existing folder. Gerber files of the latest version and images of the pcb are included. The gerber files can directly be used to order a 4 layer pcb at a pcb manufacturer, e.g.: http://www.pcb-pool.com/.
==== Firmware
If you want to upload new versions of the firmware to the BlueNodes hardware, you either need to have a J-Link debug probe from https://www.segger.com/products/debug-probes/j-link/ or you buy a Nordic development board and solder your connection by yourself. The board and all the used chips and parts are available from Mouser Electronics http://www.mouser.de/.
To compile the firmware, use EmBitz version 1.11. You can download it here: https://www.embitz.org/.
==== Android
Get the latest version of Android Studio from https://developer.android.com/studio/index.html.
== License
Schematic and pcb layout, firmware and android software is published, as far as not under the Nordic Semiconductor ASA license (Nordic LICENSE file), under the Apache 2.0 license.

== Disclaimer
Although the author takes all possible care to ensure the correctness of published information, no warranty can be accepted regarding the correctness, accuracy, uptodateness, reliability and completeness of the content of this information. The author expressly reserves the right to change, to delete or temporarily not to publish the contents wholly or partly at any time and without giving notice. Liability claims against the author because of tangible or intangible damage arising from accessing, using or not using the published information, through misuse of the connection or as a result of technical breakdowns are excluded.

=== Warning
The hardware includes a so called "Buck Converter" which does not implement galvanic separation from the mains. Therefore care must be taken and the device should never be touched when connected to mains.
While high voltage systems can be dangerous, and if not treated with care, respect and intelligence, they can result in fatal injuring. Damaging effects which can be induced in the body by electric shock or contact with live electrical systems include:
1. Ventricular fibrillation - defined by the New Oxford American Dictionary as "(of a muscle, esp. in the heart) make a quivering movement due to uncoordinated contraction of the individual fibrils." This is a potentially fatal condition where the heart muscle quivers rather than beats, eliminating blood flow and causing death.
2. Cardiac asystole - where the heart stops beating. Combined with ventricular fibrillation this constitutes cardiac arrest.
3. Respiratory arrest.
4. Burns from arc flash and resistive heating of body tissues.
5. Radio frequency burns if radio or microwave frequencies are used.
If you are not familiar with devices connected to mains and voltages higher that 35 volts, contact a specialist before connecting a BlueNodes device to mains. The author cannot be held liabile for personal injuries or other damages resulting in the use of the published schematics.
Also, a Buck Converter can emit ERM which should always be shielded. The schamatics and the pcb layout takes care about this fact.