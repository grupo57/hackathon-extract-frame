FROM public.ecr.aws/lambda/provided:al2

# Instalação do FFmpeg
RUN yum install -y tar xz zip && \
    curl -L https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz | tar -xJ && \
    mv ffmpeg-*-amd64-static/ffmpeg /opt/ && \
    chmod +x /opt/ffmpeg && \
    rm -rf ffmpeg-*-amd64-static

# Compactar o diretório para gerar o layer
RUN cd /opt && zip -r /layer.zip .

CMD ["true"]