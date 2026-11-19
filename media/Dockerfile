FROM nginx:alpine

COPY posters/ /usr/share/nginx/html/media/posters/

RUN printf 'server {\n\
    listen 80;\n\
    location /media/ {\n\
        root /usr/share/nginx/html;\n\
        add_header Access-Control-Allow-Origin *;\n\
        expires 7d;\n\
    }\n\
}\n' > /etc/nginx/conf.d/default.conf

EXPOSE 80