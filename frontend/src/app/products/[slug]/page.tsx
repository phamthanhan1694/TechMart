import StoreShell from '@/components/StoreShell'
import ProductDetailView from '@/views/catalog/ProductDetailView'

// Next 15: params là Promise, phải await trước khi dùng.
export default async function ProductDetailPage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  return (
    <StoreShell>
      <ProductDetailView slug={slug} />
    </StoreShell>
  )
}
