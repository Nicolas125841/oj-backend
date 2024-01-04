# escape=`
# Compose expects this to be tagged as 'backend'

FROM fedora:38 as oj

RUN dnf -y upgrade && `
    dnf -y install java-17-openjdk-devel && `
    dnf -y install gcc && `
    dnf -y install gcc-c++ && `
    dnf -y install pypy3.10 && `
    pypy3.10 -m ensurepip

RUN pypy3.10 --version && `
    pypy3.10 -m pip --version && `
    java -version && `
    gcc -v && `
    g++ -v

RUN mkdir /runtime && `
    mkdir /data && `
    mkdir /source && `
    mkdir /tests

EXPOSE 8080/tcp
EXPOSE 8080/udp
VOLUME ["/source"]

WORKDIR /source

ENTRYPOINT ["./mvnw", "spring-boot:run", "-f", "pom.xml"]