## 📱 Description

This Android application is designed to perform **Instance Segmentation** using the **YOLOv11** machine learning model. It brings instance segmentation functionality to Android devices by using API from server.

---

## 🚀 Getting Started

To use this repository for any custom **YOLOv8 and above** Instance Segmentation model, follow these steps:

1. **Clone the Repository**
   ```bash
   git clone git@github.com:vivekGitLearn/YOLOv11-Instance-segmentation-Android-Tflite.git
   cd YOLOv11-Instance-segmentation-Android-Tflite

2. **Update BASE_IP and PORT at java/com/vivek/yolov11instancesegmentation/Constants.kt**
   ```bash
   object Constants {
    const val BASE_IP = "192.168.125.5" (Your IP Address)
    const val BASE_PORT = "8000" (Your Port Number)
    const val BASE_URL = "http://$BASE_IP:$BASE_PORT"
    const val WS_URL = "ws://$BASE_IP:$BASE_PORT"}

3. **Update IP  at res/xml/network_security_config.xml**
   ``` bash
   <network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">192.168.125.5</domain>
    </domain-config>
   </network-security-config>
