; Bitcoin Mesh Gateway - Inno Setup Script
; Genere un installateur .exe complet

#define MyAppName "Bitcoin Mesh Gateway"
#define MyAppVersion "1.0"
#define MyAppPublisher "BitcoinLoRa"
#define MyAppURL "https://github.com/Silexperience210/BitcoinLoRa-MeshGateway"
#define MyAppExeName "run_gateway.bat"

[Setup]
AppId={{B1TC01N-M3SH-G4T3-W4Y0-1NST4LL3R}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\BitcoinMeshGateway
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=installer_output
OutputBaseFilename=BitcoinMeshGateway_Setup_v2
SetupIconFile=bitcoin_icon.ico
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=admin

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "startupicon"; Description: "Lancer au demarrage de Windows"; GroupDescription: "Options:"; Flags: unchecked

[Files]
Source: "bitcoin_mesh_gateway.py"; DestDir: "{app}"; Flags: ignoreversion
Source: "requirements.txt"; DestDir: "{app}"; Flags: ignoreversion
Source: "install.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "run_gateway.bat"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon
Name: "{commonstartup}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: startupicon

[Run]
Filename: "{app}\install.bat"; Description: "Installer Python et les dependances"; Flags: shellexec waituntilterminated
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent unchecked

[Code]
function InitializeSetup(): Boolean;
begin
  Result := True;
  // Verification supplementaire si necessaire
end;
