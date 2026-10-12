import re
p = 'android/app/src/main/AndroidManifest.xml'
s = open(p).read()
q = '''    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN"/>
            <category android:name="android.intent.category.LAUNCHER"/>
        </intent>
    </queries>
'''
comp = '''        <service android:name=".FocusService" android:exported="false" android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="focus_mode_app_blocking"/>
        </service>
        <activity android:name=".BlockActivity" android:exported="false" android:excludeFromRecents="true" android:launchMode="singleTask" android:taskAffinity="" android:theme="@android:style/Theme.Material.NoActionBar"/>
'''
perms = ''.join('    <uses-permission android:name="android.permission.%s"/>\n' % n for n in ['FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_SPECIAL_USE', 'PACKAGE_USAGE_STATS', 'SYSTEM_ALERT_WINDOW'])
assert '<application' in s and '</application>' in s
s = s.replace('<application', q + '    <application', 1)
s = s.replace('</application>', comp + '    </application>', 1)
s = s.replace('</manifest>', perms + '</manifest>', 1)
open(p, 'w').write(s)
print('manifest patched')
