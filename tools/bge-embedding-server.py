"""本地 bge-small-zh embedding 服务（OpenAI 兼容 /v1/embeddings）。

为什么需要它：Java 后端里跑不了 transformers，但项目已经有 OpenAI 兼容的
`OpenAiEmbeddingModel`，只要起一个本地服务把 bge 包成 /v1/embeddings，
后端把 `ai.embedding.provider` 切成 `openai`、base-url 指到本地即可，
不用改任何 Java 代码。

用法：
    python tools/bge-embedding-server.py            # 默认 8000 端口
    python tools/bge-embedding-server.py --port 8010

依赖：fastapi uvicorn transformers torch
模型：默认 D:\\dshWork\\models\\bge-small-zh-v1.5（可用 --model 指定）
"""
from __future__ import annotations

import argparse
import os
from pathlib import Path

import numpy as np
import torch
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import AutoModel, AutoTokenizer

DEFAULT_MODEL = r"D:\dshWork\models\bge-small-zh-v1.5"
PREFIX = "为这个句子生成表示以用于检索相关文章："

app = FastAPI(title="bge-embedding-server")
_state: dict = {}


class EmbeddingRequest(BaseModel):
    model: str | None = None
    input: str | list[str]


def load(model_dir: str) -> None:
    tok = AutoTokenizer.from_pretrained(model_dir)
    model = AutoModel.from_pretrained(model_dir).eval()
    _state["tok"] = tok
    _state["model"] = model
    _state["dim"] = int(model.config.hidden_size)


def encode(texts: list[str], query: bool = True) -> np.ndarray:
    tok, model = _state["tok"], _state["model"]
    if query:
        texts = [PREFIX + t for t in texts]
    out = []
    with torch.no_grad():
        for i in range(0, len(texts), 16):
            enc = tok(texts[i:i + 16], padding=True, truncation=True, max_length=256, return_tensors="pt")
            h = model(**enc).last_hidden_state
            m = enc["attention_mask"].unsqueeze(-1).float()
            v = (h * m).sum(1) / m.sum(1).clamp(min=1e-6)
            out.append(torch.nn.functional.normalize(v, dim=-1).numpy())
    return np.vstack(out)


@app.get("/health")
def health():
    return {"status": "ok", "dim": _state.get("dim")}


@app.post("/v1/embeddings")
def embeddings(req: EmbeddingRequest):
    if "model" not in _state:
        raise HTTPException(status_code=503, detail="model not loaded")
    texts = [req.input] if isinstance(req.input, str) else list(req.input)
    if not texts:
        raise HTTPException(status_code=400, detail="input is empty")
    try:
        vecs = encode(texts)
    except Exception as e:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=str(e)) from e
    return {
        "object": "list",
        "model": req.model or os.path.basename(str(_state.get("model_dir", ""))),
        "data": [
            {"object": "embedding", "index": i, "embedding": vecs[i].tolist()}
            for i in range(len(texts))
        ],
        "usage": {"prompt_tokens": sum(len(t) for t in texts), "total_tokens": sum(len(t) for t in texts)},
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--host", default="127.0.0.1")
    args = ap.parse_args()

    if not Path(args.model).exists():
        raise SystemExit(f"模型目录不存在: {args.model}")
    print(f"加载模型 {args.model} ...")
    load(args.model)
    _state["model_dir"] = args.model
    print(f"就绪：dim={_state['dim']}，监听 http://{args.host}:{args.port}")
    uvicorn.run(app, host=args.host, port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
