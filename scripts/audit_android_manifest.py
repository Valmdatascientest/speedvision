#!/usr/bin/env python3
"""Check the merged manifest, never print application IDs/tokens."""
import argparse
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--meta', action='store_true')
args = parser.parse_args()
root = ET.parse(Path('app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml')).getroot()
a = '{http://schemas.android.com/apk/res/android}'
permissions = {item.get(a + 'name') for item in root.findall('uses-permission')}
for forbidden in ['RECORD_AUDIO', 'READ_EXTERNAL_STORAGE', 'WRITE_EXTERNAL_STORAGE', 'ACCESS_FINE_LOCATION']:
    assert 'android.permission.' + forbidden not in permissions, forbidden
network = 'android.permission.INTERNET' in permissions
assert network == args.meta, 'Unexpected network permission profile'
app = root.find('application')
assert app.get(a + 'allowBackup') == 'false'
metadata = {item.get(a + 'name'): item.get(a + 'value') for item in app.findall('meta-data')}
services = {item.get(a + 'name') for item in app.findall('service')}
meta_service = 'com.meta.wearable.acdc.sdk.service.ACDCRegistrationService'
assert (meta_service in services) == args.meta, 'Unexpected Meta component profile'
assert root.find('uses-sdk').get(a + 'minSdkVersion') == ('29' if args.meta else '28')
if args.meta:
    for flag in ['ANALYTICS_OPT_OUT', 'CRASH_REPORTING_OPT_OUT']:
        assert metadata.get('com.meta.wearable.mwdat.' + flag) == 'true', flag
    assert 'android.permission.BLUETOOTH_CONNECT' in permissions
else:
    assert not any('BLUETOOTH' in permission for permission in permissions)
print('Merged manifest profile verified:', 'Meta opt-in' if args.meta else 'local-only')
