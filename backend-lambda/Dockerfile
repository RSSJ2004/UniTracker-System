FROM public.ecr.aws/lambda/nodejs:20 AS build
WORKDIR /build
COPY package*.json tsconfig*.json ./
RUN npm ci
COPY src ./src
RUN npm run build

FROM public.ecr.aws/lambda/nodejs:20
WORKDIR ${LAMBDA_TASK_ROOT}
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=build /build/dist ./dist

# ¡AQUÍ ESTÁ LA MAGIA! Copia tu sesión generada localmente al contenedor
COPY storageState.json ./

CMD ["dist/index.handler"]