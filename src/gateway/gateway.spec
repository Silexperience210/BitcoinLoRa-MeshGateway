# -*- mode: python ; coding: utf-8 -*-

block_cipher = None

a = Analysis(
    ['bitcoin_mesh_gateway.py'],
    pathex=[],
    binaries=[],
    datas=[],
    hiddenimports=[
        'meshtastic',
        'meshtastic.serial_interface',
        'pubsub',
        'pubsub.core',
        'pubsub.core.imp2',
        'serial',
        'serial.tools',
        'serial.tools.list_ports',
        'requests',
        'socks',
        'tkinter',
        'tkinter.ttk',
        'tkinter.scrolledtext',
        'tkinter.messagebox',
        'threading',
        'struct',
        'time',
        'json',
        'hashlib',
        'sys',
        'argparse'
    ],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)

pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.zipfiles,
    a.datas,
    [],
    name='BitcoinMeshGateway',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,  # Pas de console, interface graphique uniquement
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=None,  # Peut être ajouté plus tard
)