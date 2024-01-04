# escape=`
# Compose expects this to be tagged as 'backend'

FROM fedora:38 as safe-runtime

RUN dnf -y upgrade && `
    dnf -y install java-17-openjdk-devel && `
    dnf -y install gcc && `
    dnf -y install gcc-c++ && `
    dnf -y install pypy3.10 && `
    dnf -y install firejail && `
    pypy3.10 -m ensurepip

RUN pypy3.10 --version && `
    pypy3.10 -m pip --version && `
    java -version && `
    gcc -v && `
    g++ -v

RUN mkdir /runtime && `
    mkdir /source && `
    mkdir /tests

VOLUME ["/source"]