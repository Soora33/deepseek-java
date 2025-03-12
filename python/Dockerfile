# 使用官方 Python 镜像作为基础镜像
FROM python:3.11-slim-bullseye

# 设置工作目录
WORKDIR /app

# 将 requirements.txt 复制到工作目录
COPY requirements.txt .
COPY ./model /app/model
COPY ./data /app/data

# 安装依赖
RUN pip install --no-cache-dir -r requirements.txt \
    --index-url https://download.pytorch.org/whl/cpu \
    --extra-index-url https://pypi.org/simple

# 将 Python 脚本复制到工作目录
COPY app.py .

# 定义容器启动时执行的命令
CMD ["python", "app.py"]