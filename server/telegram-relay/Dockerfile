FROM python:3.12-slim-bookworm
WORKDIR /app
COPY relay.py /app/relay.py
USER 10001:10001
EXPOSE 8080
CMD ["python", "-B", "-u", "/app/relay.py"]
