本项目是基于 Java 实现的 deepseek 调用案例，并集成了联网搜索和本地知识库功能

## 项目结构
项目分为 Java 代码和 Python 代码两部分。
Java 代码位于 src 目录下，主要是 deepseek 的调用和联网搜索功能。
Python 代码位于 python 目录下，主要是 m3e 模型的调用，实现本地知识库功能。
## 环境要求
Java 版本：17 及以上

Python 版本：3.11

自备 deepseek的key 和 tavily的key（联网搜索引擎）

## 如何使用
1. 下载项目代码
2. 打开 `application.yml`，配置 deepseek 的 key 和 tavily 的 key（es 部分不用动， 目前已经把 es 的部分优化掉了，保留代码作为未来向量搜索的参考）
3. 运行 SpringBoot 项目，启动成功后，已实现deepseek-r1对话及联网搜索功能
4. 进入 python 目录，执行 `pip install -r requirements.txt` 安装所需依赖
5. 在 python 目录下，执行 `git clone https://huggingface.co/moka-ai/m3e-base ./model/m3e` 下载向量模型，再手动下载确实的 2 个大文件，详情参考 python 目录下的 readme 文件
6. 配置 python 目录下的 `config.json`，参考 python 目录下的 readme 文件
5. 运行 `app.py`，启动 python 脚本
6. 调用 `localhost:5001/api/generate-index` 接口（POST）, 生成原文件的索引（元文件可参考 jsonData.json），传入文件类型参数 file，文件后缀为 json
7. 将自己的元文件放在 python 目录下的 data 目录下（索引文件会自动生成在 data 目录下，且生成的名字与默认使用索引的一致，所以无需修改），重启 app.py，即可实现本地知识库功能
8. 调用 Java 接口 `localhost:7780/api/chat`（POST），参数示例：
````java
{
   "message": "10.11 和 10.12 哪个更大",
   // 是否启用联网
   "useSearch": true,
   // 是否启用知识库
   "useRAG": true,
   // 是否启用知识库最大阈值
   "maxToggle": true
}
````
更详细的使用说明参考：[在 java 中使用 deepseek 并接入联网搜索和知识库](https://33sora.com/posts/a39037a1.html)
