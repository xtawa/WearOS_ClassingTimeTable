"""Regression guard for the local-only BYOK boundary. No credentials or devices needed."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
private='classing_byok_private.xml'
for module in ('mobile','app'):
    rules=root/module/'src/main/res/xml'
    backup=ET.parse(rules/'backup_rules.xml').getroot()
    assert any(e.attrib=={'domain':'sharedpref','path':private} for e in backup.findall('exclude'))
    extraction=ET.parse(rules/'data_extraction_rules.xml').getroot()
    for flow in ('cloud-backup','device-transfer'):
        assert any(e.attrib=={'domain':'sharedpref','path':private} for e in extraction.find(flow).findall('exclude'))
    manifest=ET.parse(root/module/'src/main/AndroidManifest.xml').getroot()
    ns='{http://schemas.android.com/apk/res/android}'
    permissions={e.get(ns+'name') for e in manifest.findall('uses-permission')}
    assert 'android.permission.BLUETOOTH_CONNECT' in permissions
    app=manifest.find('application')
    assert app.get(ns+'dataExtractionRules')=='@xml/data_extraction_rules'
    assert app.get(ns+'fullBackupContent')=='@xml/backup_rules'

common=root/'client-common/src/main/java/com/classing/client/byok'
bluetooth=(common/'ByokBluetooth.kt').read_text(encoding='utf-8')
assert 'createRfcommSocketToServiceRecord' in bluetooth and 'listenUsingRfcommWithServiceRecord' in bluetooth
assert 'import com.google.android.gms.wearable' not in bluetooth
assert 'http://' not in bluetooth and 'https://' not in bluetooth
assert 'hello.getString("appId")==context.packageName' in bluetooth
assert 'ownerId+":"+context.packageName' in bluetooth
assert 'config.forWear()' in bluetooth and 'ack=="SAVED"' in bluetooth
store=(common/'ByokStore.kt').read_text(encoding='utf-8')
assert 'EncryptedSharedPreferences.create' in store
provider=(common/'ByokProvider.kt').read_text(encoding='utf-8')
assert 'setRequestProperty("Authorization","Bearer ${profile.apiKey}")' in provider
assert 'connection.instanceFollowRedirects=false' in provider
assert 'com.xtawa.classingtime.sync' not in provider and 'com.google.android.gms.wearable' not in provider

# Cloud, WebDAV, Wear Data Layer and timetable serializers must not load the private store.
checked=0
for base in (root/'mobile/src/main/java',root/'app/src/main/java',root/'client-common/src/main/java'):
    for path in base.rglob('*.kt'):
        relative=path.relative_to(base)
        if any(part in {'sync','data','cloud','schedule','exam'} for part in relative.parts):
            text=path.read_text(encoding='utf-8')
            assert 'ByokStore' not in text and 'classing_byok_private' not in text, path
            checked+=1
print(f'BYOK boundary passed: both backup formats, Bluetooth-only transfer, direct-provider authorization, {checked} serializers/sync files')
