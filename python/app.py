import json
import os
import tempfile
from pathlib import Path
from typing import List, Dict

import faiss
from flask import Flask, request, jsonify
from sentence_transformers import SentenceTransformer

app = Flask(__name__)

# 加载配置文件
with open('config.json', 'r', encoding='utf-8') as f:
    CONFIG = json.load(f)

data_fields = CONFIG["data_fields"]
required_data_fields = ["metadata_fields", "content_field"]
for field in required_data_fields:
    if field not in data_fields:
        raise KeyError(f"Missing required data_fields config: {field}")
metadata_fields = data_fields["metadata_fields"]
content_field = data_fields["content_field"]

# 初始化模型
if not Path(CONFIG["model_path"]).exists():
    raise FileNotFoundError(f"模型未找到: {CONFIG['model_path']}")
model = SentenceTransformer(CONFIG["model_path"])

class VectorSearchSystem:
    def __init__(self):
        self.index = None
        self.documents = []
        self._auto_load()

    def _auto_load(self):
        """自动加载持久化数据"""
        try:
            # 加载FAISS索引
            if os.path.exists(CONFIG["index_file"]):
                self.index = faiss.read_index(CONFIG["index_file"])
            else:
                self.initialize_index()

            # 加载文档元数据
            if os.path.exists(CONFIG["json_data_file"]):
                with open(CONFIG["json_data_file"], 'r', encoding='utf-8') as f:
                    self.documents = json.load(f)
            else:
                self.documents = []

        except Exception as e:
            print(f"[ERROR] 数据加载失败: {str(e)}")
            self.initialize_index()
            self.documents = []

    def initialize_index(self):
        """创建新索引"""
        self.index = faiss.IndexFlatIP(CONFIG["vector_dim"])

    def search(self, query: str, top_k: int = None) -> List[Dict]:
        top_k = top_k or CONFIG["default_top_k"]
        query_vector = model.encode([query], normalize_embeddings=True).astype('float32')
        distances, indices = self.index.search(query_vector, top_k*2)  # 扩大召回范围
        results = []
        for idx, score in zip(indices[0], distances[0]):
            if score < CONFIG["similarity_threshold"]:
                continue  # 关键点：严格阈值过滤
            if 0 <= idx < len(self.documents):
                results.append({
                    **self.documents[idx],
                    "similarity_score": float(score)
                })

        # 二次排序并截断
        return sorted(results, key=lambda x: x["similarity_score"], reverse=True)[:top_k]

# 初始化系统
search_system = VectorSearchSystem()

def format_search_result(result: Dict) -> str:
    """格式化单个搜索结果"""
    content_text = result.get(content_field, "")

    # 处理元数据字段
    metadata_lines = []
    for field in metadata_fields:
        value = result.get(field, "")
        if value:
            metadata_lines.append(f"{value}\n")

    # 组合元数据和内容
    formatted_metadata = "".join(metadata_lines)
    formatted_content = f"{content_text}\n" if content_text else ""

    return f"{formatted_metadata}{formatted_content}"

@app.route('/api/search', methods=['GET'])
def handle_search():
    """搜索接口"""
    query = request.args.get('query')
    top_k = request.args.get('top_k', type=int)

    if not query:
        return jsonify({"error": "Missing query parameter"}), 400

    try:
        results = search_system.search(query, top_k=top_k)
        # 按照相似度分数排序
        sorted_results = sorted(results, key=lambda x: x["similarity_score"], reverse=True)
        # 格式化输出
        formatted_output = "\n".join([format_search_result(r) for r in sorted_results])
        return app.response_class(
            response=formatted_output,
            status=200,
            mimetype='text/plain'
        )
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/generate-index', methods=['POST'])
def generate_index():
    """
    生成临时索引接口
    接收JSON文件 → 生成FAISS索引 → 返回索引文件和对应的处理后的JSON
    """
    if 'file' not in request.files:
        return jsonify({"error": "No file uploaded"}), 400

    file = request.files['file']
    if file.filename == '':
        return jsonify({"error": "Empty filename"}), 400

    try:
        # 使用临时目录处理
        with tempfile.TemporaryDirectory() as tmp_dir:
            # 解析输入数据
            documents = json.load(file)

            # 验证数据格式
            required_fields = metadata_fields + [content_field]
            for doc in documents:
                if not all(field in doc for field in required_fields):
                    missing = [field for field in required_fields if field not in doc]
                    raise ValueError(f"Document missing fields: {missing}")

            # 生成向量
            contents = [doc[content_field] for doc in documents]
            vectors = model.encode(contents, normalize_embeddings=True).astype('float32')

            # 创建临时索引
            tmp_index_path = Path(tmp_dir) / "temp_index.index"
            index = faiss.IndexFlatIP(CONFIG["vector_dim"])
            index.add(vectors)
            faiss.write_index(index, str(tmp_index_path))

            # 生成带ID的元数据
            processed_data = [
                {**{field: doc[field] for field in metadata_fields},
                 content_field: doc[content_field],
                 "vector_id": idx}
                for idx, doc in enumerate(documents)
            ]

            # 保存临时JSON
            tmp_json_path = Path(tmp_dir) / "processed_data.json"
            with open(tmp_json_path, 'w', encoding='utf-8') as f:
                json.dump(processed_data, f, ensure_ascii=False)


            index = faiss.IndexFlatIP(CONFIG["vector_dim"])
            index.add(vectors)
            faiss.write_index(index, str(CONFIG['faiss_dir']))

            # 打包返回文件（示例保留索引文件）
            return "200"
            # return send_file(
            #     tmp_index_path,
            #     mimetype='application/octet-stream',
            #     as_attachment=True,
            #     download_name="generated_index.index"
            # )

    except json.JSONDecodeError:
        return jsonify({"error": "Invalid JSON format"}), 400
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    os.makedirs('data', exist_ok=True)
    app.run(host='0.0.0.0', port=CONFIG["server_port"], debug=CONFIG["debug"])
