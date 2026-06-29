Name:           suwayomi-server
Version:        $pkgver
Release:        $pkgrel
License:        MPL-2.0
Group:          web
URL:            https://suwayomi.org
Source0:        suwayomi-server_$pkgver.orig.tar.gz
Summary:        A free and open source manga reader server that runs extensions built for Tachiyomi.
BuildArch:      x86_64
Requires:       java-21-openjdk

%description
A free and open source manga reader server that runs extensions built for Tachiyomi.
Suwayomi is an independent Tachiyomi compatible software and is not a Fork of Tachiyomi.

See CHANGELOG.md on https://github.com/Suwayomi/Suwayomi-Server

%global debug_package %{nil}

%prep
%setup -q

%build

%install
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}%{_datadir}
mkdir -p %{buildroot}%{_libdir}
mkdir -p %{buildroot}/etc/suwayomi

mkdir -p %{buildroot}%{_datadir}/{java,pixmaps,applications}
mkdir -p %{buildroot}%{_datadir}/java/%{name}/bin
mkdir -p %{buildroot}%{_libdir}/{systemd,sysusers.d,tmpfiles.d}
mkdir -p %{buildroot}%{_libdir}/systemd/system


install -m 0755 Suwayomi-Server.jar       %{buildroot}%{_datadir}/java/suwayomi-server/bin/Suwayomi-Server.jar
install -m 0755 Suwayomi-Launcher.jar     %{buildroot}%{_datadir}/java/suwayomi-server/Suwayomi-Launcher.jar
install -m 0755 %{name}.png               %{buildroot}%{_datadir}/pixmaps/%{name}.png
install -m 0755 %{name}.desktop           %{buildroot}%{_datadir}/applications/%{name}.desktop
install -m 0755 suwayomi-launcher.desktop %{buildroot}%{_datadir}/applications/suwayomi-launcher.desktop
install -m 0755 %{name}.service           %{buildroot}%{_libdir}/systemd/system/%{name}.service
install -m 0755 %{name}.sysusers          %{buildroot}%{_libdir}/sysusers.d/%{name}.conf
install -m 0755 %{name}.tmpfiles          %{buildroot}%{_libdir}/tmpfiles.d/%{name}.conf
install -m 0755 %{name}.conf              %{buildroot}/etc/suwayomi/server.conf
install -m 0755 %{name}.sh                %{buildroot}%{_bindir}/%{name}
install -m 0755 suwayomi-launcher.sh      %{buildroot}%{_bindir}/suwayomi-launcher
install -m 0755 catch_abort.so            %{buildroot}%{_datadir}/java/%{name}/bin/catch_abort.so

%files
%{_datadir}/java/suwayomi-server/bin/Suwayomi-Server.jar
%{_datadir}/java/suwayomi-server/Suwayomi-Launcher.jar
%{_datadir}/pixmaps/%{name}.png
%{_datadir}/applications/%{name}.desktop
%{_datadir}/applications/suwayomi-launcher.desktop
%{_libdir}/systemd/system/%{name}.service
%{_libdir}/sysusers.d/%{name}.conf
%{_libdir}/tmpfiles.d/%{name}.conf
/etc/suwayomi/server.conf
%{_bindir}/%{name}
%{_bindir}/suwayomi-launcher
%{_datadir}/java/%{name}/bin/catch_abort.so

