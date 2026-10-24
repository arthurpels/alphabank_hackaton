import { useEffect, useMemo } from 'react';
import { Button } from '@alfalab/core-components/button';
import { Modal } from '@alfalab/core-components/modal';
import './DocumentPreview.css';

type Props = {
  blob: Blob;
  onClose: () => void;
};

const extension = (type: string) => (type === 'image/jpeg' ? 'jpg' : type.split('/')[1] ?? 'pdf');

export function DocumentPreview({ blob, onClose }: Props) {
  const url = useMemo(() => URL.createObjectURL(blob), [blob]);
  useEffect(() => () => URL.revokeObjectURL(url), [url]);
  const isImage = blob.type.startsWith('image/');

  return (
    <Modal open onClose={onClose} size={800}>
      <Modal.Header hasCloser title="Документ клиента" />
      <Modal.Content>
        <div className="doc-preview">
          {isImage ? (
            <img className="doc-preview__image" src={url} alt="Документ клиента" />
          ) : (
            <iframe className="doc-preview__frame" src={url} title="Документ клиента" />
          )}
        </div>
      </Modal.Content>
      <Modal.Footer>
        <Button view="secondary" size={48} href={url} download={`document.${extension(blob.type)}`}>
          Скачать
        </Button>
        <Button view="accent" size={48} onClick={onClose}>
          Закрыть
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
